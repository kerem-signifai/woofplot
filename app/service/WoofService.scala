package service

import java.util.concurrent.{Executors, TimeUnit}

import dao.{MetricDAO, WoofDAO}
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.{Metric, SensorPayload, SensorType, Woof}
import org.apache.logging.log4j.scala.Logging
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.Function.tupled
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class WoofService @Inject()(
	applicationLifecycle: ApplicationLifecycle,
	config: Configuration,
	messageService: MessageService,
	metricDAO: MetricDAO,
	woofDAO: WoofDAO
)(implicit ec: ExecutionContext) extends Logging {

	private val loadPeriod = config.get[FiniteDuration]("load_daemon.period")
	private val maxLoadHistory = config.get[Int]("load_daemon.max_history_sync")
	private val scheduler = Executors newScheduledThreadPool 1

	private val task = scheduler scheduleWithFixedDelay(() => fetchData(), 0, loadPeriod.toMillis, TimeUnit.MILLISECONDS)
	applicationLifecycle addStopHook (() => Future.successful(task.cancel(true)))

	def createWoof(woof: Woof): Future[Any] = woofDAO.insertWoof(woof)
	def listWoofs: Future[Seq[Woof]] = woofDAO.listWoofs
	def updateWoof(url: String, source: Woof): Future[Any] = woofDAO.updateWoof(url, source)
	def deleteWoof(url: String): Future[Any] = woofDAO.deleteWoof(url)

	def queryWoofs(source: String, from: Long, to: Long, interval: Interval, agg: Aggregation): Future[Seq[Metric]] = metricDAO queryMetrics(source, from, to, interval, agg)

	def peekWoof(source: String): Future[SensorPayload] = {
		messageService.getLatestSeqNo(source).flatMap(seqNo => messageService.fetch(source, seqNo))
	}

	def fetchData(): Unit = {
		logger.info("Fetching data")
		val req = listWoofs flatMap (Future sequence _.map(syncWoof))
		try {
			Await.result(req, 30 seconds)
		} catch {
			case e: Throwable => logger.error("Failed to sync data", e)
		}
	}

	def syncWoof(woof: Woof): Future[Any] = {
		logger.info(s"Loading new metrics from woof ${woof.url}")

		metricDAO.latestSeqNo(woof.url).flatMap { storedSeqNo =>
			messageService.getLatestSeqNo(woof.url).flatMap { latestSeqNo =>
				val minSeqNo = storedSeqNo match {
					case Some(stored) => math.max(latestSeqNo - maxLoadHistory, stored + 1)
					case _ => latestSeqNo - maxLoadHistory
				}

				logger.info(s"Fetching metrics from $minSeqNo to $latestSeqNo")
				Future sequence (minSeqNo to latestSeqNo map (messageService.fetch(woof.url, _))) flatMap (payloads => ingestMetrics(woof, payloads))
			}
		}
	}

	def ingestMetrics(woof: Woof, payloads: Seq[SensorPayload]): Future[Any] = {
		logger.info(s"Ingesting ${payloads.size} metrics for woof ${woof.url}")

		payloads.headOption match {
			case Some(SensorPayload(SensorType.NUMERIC, _, _, _, _)) =>
				metricDAO insertMetrics payloads.map {
					case SensorPayload(SensorType.NUMERIC, None, Some(num), timestamp, seqNo) => Metric(s"${woof.url}:${woof.dataLabels.head}", woof.url, timestamp, num, seqNo)
					case p => throw new IllegalArgumentException(s"Invalid payload received: $p")
				}
			case Some(SensorPayload(SensorType.TEXT, _, _, _, _)) =>
				metricDAO insertMetrics payloads.flatMap {
					case SensorPayload(SensorType.TEXT, Some(text), None, timestamp, seqNo) =>
						woof.pattern match {
							case Some(patternText) =>
								val pattern = patternText.r
								text match {
									case pattern(groups@_*) =>
										groups zip woof.dataLabels map tupled { (value, label) => Metric(s"${woof.url}:$label", woof.url, timestamp, value.toDouble, seqNo) }
									case unmatched =>
										throw new IllegalArgumentException(s"Unable to parse woof data [$text] for woof ${woof.url} - $unmatched")
								}
							case _ => throw new IllegalArgumentException(s"No pattern defined to handle text for woof ${woof.url}")
						}
					case p => throw new IllegalArgumentException(s"Invalid payload received: $p")
				}
			case None => Future.unit
		}
	}
}
