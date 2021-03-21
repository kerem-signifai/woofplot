package service

import akka.actor.{ActorSystem, Scheduler}
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval, Moment, Raw}
import model.{Metric, SensorPayload, SensorType, Woof, WoofBlueprint}
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
    val task = scheduler.scheduleWithFixedDelay(loadInitialDelay, loadPeriod)(() => reloadWoofs())

    applicationLifecycle addStopHook (() => Future.successful(task.cancel()))
  }

  def getRetentionPolicy: Future[Option[FiniteDuration]] = metricDAO.getRetentionPolicy
  def setRetentionPolicy(weeks: Int): Future[Any] = {
    require(weeks > 0, "Retention duration must be positive")
    metricDAO.setRetentionPolicy(weeks)
  }
  def deleteRetentionPolicy(): Future[Any] = metricDAO.deleteRetentionPolicy()

  def updateWoof(woofId: Long, bp: WoofBlueprint): Future[Any] = {
    val fieldIds = bp.columns.map(_.field)
    require(fieldIds.toSet.size == fieldIds.size, "Column fields must be unique")
    fetchWoof(woofId).flatMap {
      case Some(woof) =>
        val addedColumns = bp.columns diff woof.columns
        val deletedColumns = woof.columns diff bp.columns
        val drop = if (woof.url == bp.url) {
          Future.sequence(deletedColumns.map(c => metricDAO.dropField(woofId, c.field)))
        } else {
          metricDAO.dropWoof(woofId)
        }
        woofDAO
          .updateWoof(woofId, bp)
          .flatMap(w => drop.map(_ => w))
          .flatMap { w =>
            if (addedColumns.isEmpty) Future.unit
            else syncWoof(w, Some(defaultLoadHistory))
          }

      case _ => throw new IllegalArgumentException(s"Unable to find woof $woofId")
    }
  }

  def createWoof(bp: WoofBlueprint): Future[Any] = {
    val fieldIds = bp.columns.map(_.field)
    require(fieldIds.toSet.size == fieldIds.size, "Column fields must be unique")
    messageService.getLatestSeqNo(bp.url) flatMap { _ =>
      woofDAO.insertWoof(bp) map { syncWoof(_, Some(defaultLoadHistory)) }
    }
  }

  def fetchWoof(woofId: Long): Future[Option[Woof]] = woofDAO.fetchWoof(woofId)

  def listWoofs: Future[Seq[Woof]] = woofDAO.listWoofs

  def deleteWoof(woofId: Long): Future[Any] = Future.sequence(woofDAO.deleteWoof(woofId) :: metricDAO.dropWoof(woofId) :: Nil)

  def queryWoofs(
    woofId: Long,
    field: Int,
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
    metricDAO queryMetrics(woofId, field, from, to, interval, agg, rawElements)
  }

  def peekWoof(woofUrl: String): Future[SensorPayload] = messageService.fetch(woofUrl, 1).map(_.head)

  def reloadWoofs(): Unit = {
    logger.info("Reloading woofs")
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
    logger.info(s"Loading new metrics from woof ${woof.woofId} [${woof.url}]")
    if (loadHistory.exists(_ > maxLoadHistory)) {
      throw new IllegalArgumentException(s"History too high; maximum: $maxLoadHistory")
    }

    messageService.getLatestSeqNo(woof.url) flatMap { latestSeqNo =>
      val numLoad = 1 + latestSeqNo - woof.latestSeqNo
      messageService.fetch(woof.url, loadHistory.getOrElse(math.min(maxLoadHistory, numLoad.toInt)))
        .map { ingestMetrics(woof, _) }
        .map { _ => woofDAO.updateWoofSeqNo(woof.woofId, latestSeqNo) }
    }
  }

  private def findDelimiter(textPayload: String): Char = Seq(':', ',', ' ').maxBy(c => textPayload.count(_ == c))

  def ingestMetrics(woof: Woof, payloads: Seq[SensorPayload]): Future[Any] = {
    logger.info(s"Ingesting ${payloads.size} metrics for woof ${woof.url}")

    val columns = woof.columns
    payloads.headOption match {
      case Some(SensorPayload(SensorType.NUMERIC, _, _, _)) =>
        val conversion = columns.head.conversion.fx
        metricDAO.insertMetrics(payloads.map {
          case SensorPayload(SensorType.NUMERIC, None, Some(num), timestamp) => Metric(woof.woofId, 0, timestamp, conversion(num))
          case p => throw new IllegalArgumentException(s"Invalid payload received: $p")
        })
      case Some(SensorPayload(SensorType.TEXT, _, _, _)) =>

        metricDAO.insertMetrics(payloads.flatMap {
          case SensorPayload(SensorType.TEXT, Some(text), None, timestamp) =>
            val delim = findDelimiter(text)
            val data = text.split(delim).lift
            columns flatMap { f =>
              data(f.field) match {
                case Some(value) =>
                  value.toDoubleOption map { number =>
                    Metric(woof.woofId, f.field, timestamp, f.conversion.fx(number))
                  } orElse {
                    logger.error(s"Failed to parse $value as a double for woof ${woof.woofId} field ${f.field}")
                    None
                  }
                case None =>
                  logger.error(s"Failed to parse woof data [$text] for woof ${woof.woofId} field ${f.field}")
                  None
              }
            }
          case p => throw new IllegalArgumentException(s"Invalid payload received: $p")
        })
      case None => Future.unit
    }
  }
}
