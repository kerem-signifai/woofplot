package service

import akka.actor.{ActorSystem, Scheduler}
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
	woofDAO: WoofDAO,
	actorSystem: ActorSystem
)(implicit ec: ExecutionContext) extends Logging {

	private final val RELOAD_TIMEOUT = 30 seconds

	private val loadTaskEnabled = config.get[Boolean]("load_daemon.enabled")
	private val loadPeriod = config.get[FiniteDuration]("load_daemon.period")
	private val maxLoadHistory = config.get[Int]("load_daemon.max_history_sync")
	private val defaultLoadHistory = config.get[Int]("load_daemon.default_history_sync")

	if (loadTaskEnabled) {
		val scheduler: Scheduler = actorSystem.scheduler
		val task = scheduler schedule(0 millis, loadPeriod, () => reloadSources())

		applicationLifecycle addStopHook (() => Future.successful(task.cancel()))
	}

	def createWoof(woof: Woof): Future[Any] = woofDAO.insertWoof(woof).map(_ => syncWoof(woof, defaultLoadHistory, force = true))
	def fetchWoof(url: String): Future[Option[Woof]] = woofDAO.fetchWoof(url)
	def listWoofs: Future[Seq[Woof]] = woofDAO.listWoofs
	def updateWoof(url: String, source: Woof): Future[Any] = woofDAO.updateWoof(url, source)
	def deleteWoof(url: String): Future[Any] = woofDAO.deleteWoof(url)

	def queryWoofs(source: String, from: Long, to: Long, interval: Interval, agg: Aggregation): Future[Seq[Metric]] = metricDAO queryMetrics(source, from, to, interval, agg)

	def peekWoof(source: String): Future[SensorPayload] = {
		messageService.getLatestSeqNo(source).flatMap(seqNo => messageService.fetch(source, seqNo))
	}

	def reloadSources(): Unit = {
		logger.info("Reloading sources")
		val req = listWoofs flatMap (Future sequence _.map(syncWoof(_, defaultLoadHistory, force = false)))
		try {
			Await.result(req, RELOAD_TIMEOUT)
		} catch {
			case e: Throwable => logger.error("Failed to sync data", e)
		}
	}

	def syncWoof(woof: Woof, loadHistory: Int, force: Boolean): Future[Any] = {
		logger.info(s"Loading new metrics from woof ${woof.url}")
		if (loadHistory > maxLoadHistory) {
			throw new IllegalArgumentException(s"History too high; maximum: $maxLoadHistory")
		}

		metricDAO.latestSeqNo(woof.url).flatMap { storedSeqNo =>
			messageService.getLatestSeqNo(woof.url).flatMap { latestSeqNo =>
				val minSeqNo = storedSeqNo match {
					case Some(stored) if !force => math.max(latestSeqNo - loadHistory, stored + 1)
					case _ => latestSeqNo - loadHistory
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
				val field = woof.fields.head
				val conversion = field.conversion.fx
				metricDAO insertMetrics payloads.map {
					case SensorPayload(SensorType.NUMERIC, None, Some(num), timestamp, seqNo) => Metric(s"${woof.url}:${field.label}", woof.url, timestamp, conversion(num), seqNo)
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
										if (groups.size == woof.fields.size) {
											groups zip woof.fields map tupled { (value, field) =>
												val conversion = field.conversion.fx
												Metric(s"${woof.url}:${field.label}", woof.url, timestamp, conversion(value.toDouble), seqNo) }
										} else {
											throw new IllegalArgumentException(s"Failed to extract data for all fields: ${woof.fields}")
										}
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
