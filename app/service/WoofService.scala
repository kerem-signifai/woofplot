package service

import akka.actor.{ActorSystem, Scheduler}
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval, Moment, Raw}
import model.{Metric, SensorPayload, SensorType, Woof}
import org.apache.logging.log4j.scala.Logging
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import service.store.{MetricStore, WoofStore}

import scala.Function.tupled
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class WoofService @Inject()(
  applicationLifecycle: ApplicationLifecycle,
  config: Configuration,
  messageService: MessageService,
  metricDAO: MetricStore,
  woofDAO: WoofStore,
  actorSystem: ActorSystem
)(implicit ec: ExecutionContext) extends Logging {

  private val loadTaskEnabled = config.get[Boolean]("load_daemon.enabled")
  private val loadPeriod = config.get[FiniteDuration]("load_daemon.period")
  private val loadInitialDelay = config.get[FiniteDuration]("load_daemon.initial_delay")
  private val loadTimeout = config.get[FiniteDuration]("load_daemon.timeout")
  private val maxLoadHistory = config.get[Int]("load_daemon.max_history_sync")
  private val defaultLoadHistory = config.get[Int]("load_daemon.default_history_sync")

  if (loadTaskEnabled) {
    val scheduler: Scheduler = actorSystem.scheduler
    val task = scheduler.scheduleWithFixedDelay(loadInitialDelay, loadPeriod)(() => reloadSources())

    applicationLifecycle addStopHook (() => Future.successful(task.cancel()))
  }

  def createWoof(woof: Woof): Future[Any] = {
    messageService.getLatestSeqNo(woof.url) flatMap { _ =>
      woofDAO.insertWoof(woof) map { _ => syncWoof(woof, Some(defaultLoadHistory)) }
    }
  }

  def fetchWoof(url: String): Future[Option[Woof]] = woofDAO.fetchWoof(url)

  def listWoofs: Future[Seq[Woof]] = woofDAO.listWoofs

  def deleteWoof(url: String): Future[Any] = Future.sequence(woofDAO.deleteWoof(url) :: metricDAO.dropWoof(url) :: Nil)

  def queryWoofs(
    source: String,
    from: Option[Long],
    to: Option[Long],
    interval: Interval,
    agg: Aggregation,
    rawElements: Option[Int]
  ): Future[Seq[Metric]] = {
    rawElements.foreach { _ =>
      require(agg == Raw, "Aggregation must be `Raw` when bounding element count")
      require(interval == Moment, "Interval must be `Moment` when bounding element count")
    }
    metricDAO queryMetrics(source, from, to, interval, agg, rawElements)
  }

  def peekWoof(source: String): Future[SensorPayload] = messageService.fetch(source, 1).map(_.head)

  def reloadSources(): Unit = {
    logger.info("Reloading sources")
    val req = listWoofs flatMap {
      Future sequence _.map(syncWoof(_, None))
    }
    try {
      Await.result(req, loadTimeout)
    } catch {
      case e: Throwable => logger.error("Failed to sync data", e)
    }
  }

  def syncWoof(woof: Woof, loadHistory: Option[Int]): Future[Any] = {
    logger.info(s"Loading new metrics from woof ${woof.url}")
    if (loadHistory.exists(_ > maxLoadHistory)) {
      throw new IllegalArgumentException(s"History too high; maximum: $maxLoadHistory")
    }

    messageService.getLatestSeqNo(woof.url) flatMap { latestSeqNo =>
      val numLoad = 1 + latestSeqNo - woof.latestSeqNo
      messageService.fetch(woof.url, loadHistory.getOrElse(math.min(maxLoadHistory, numLoad.toInt))) map (
        ingestMetrics(woof, _)
        ) map { _ => woofDAO.updateWoofSeqNo(woof, latestSeqNo) }
    }
  }

  def ingestMetrics(woof: Woof, payloads: Seq[SensorPayload]): Future[Any] = {
    logger.info(s"Ingesting ${payloads.size} metrics for woof ${woof.url}")

    payloads.headOption match {
      case Some(SensorPayload(SensorType.NUMERIC, _, _, _)) =>
        val field = woof.fields.head
        val conversion = field.conversion.fx
        metricDAO insertMetrics payloads.map {
          case SensorPayload(SensorType.NUMERIC, None, Some(num), timestamp) => Metric(s"${woof.url}:${field.label}", woof.url, timestamp, conversion(num))
          case p => throw new IllegalArgumentException(s"Invalid payload received: $p")
        }
      case Some(SensorPayload(SensorType.TEXT, _, _, _)) =>
        metricDAO insertMetrics payloads.flatMap {
          case SensorPayload(SensorType.TEXT, Some(text), None, timestamp) =>
            woof.pattern match {
              case Some(patternText) =>
                val pattern = patternText.r
                text match {
                  case pattern(groups@_*) =>
                    if (groups.size == woof.fields.size) {
                      groups zip woof.fields flatMap tupled { (value, field) =>
                        val conversion = field.conversion.fx
                        value.toDoubleOption map { number =>
                          Metric(s"${woof.url}:${field.label}", woof.url, timestamp, conversion(number))
                        }
                      }
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
