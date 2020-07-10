package service.store.localfs

import akka.actor.ActorSystem
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Codec._
import model.Metric
import model.Query._
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import service.store.MetricStore

import scala.Function.tupled
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class FSMetricStore @Inject()()(implicit
  ec: ExecutionContext,
  config: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
) extends FileBackedStore("metrics") with MetricStore with Logging {
  private implicit val orderingByTs: Ordering[Metric] = Ordering.by(e => e.timestamp)
  private val store = new java.util.concurrent.ConcurrentHashMap[String, mutable.Set[Metric]]().asScala

  initFSBackedStore()

  override def serialize: String = store.asJson.toString

  override def deserialize(payload: String): Either[Throwable, Int] = decode[Map[String, Seq[Metric]]](payload) map { data =>
    Await.result(
      Future.sequence(
        data map tupled { (src, metrics) =>
          logger.info(s"Found ${metrics.size} metrics for source $src")
          insertMetrics(metrics) map { _ => metrics.size }
        }
      ), Duration.Inf).sum
  }

  override def insertMetrics(metrics: Seq[Metric]): Future[Any] = {
    metrics.groupBy(_.source) foreach {
      case (source, data) =>
        val sorted = store.getOrElseUpdate(source, new java.util.concurrent.ConcurrentSkipListSet[Metric](orderingByTs).asScala)
        sorted ++= data
    }
    Future.unit
  }

  override def queryMetrics(source: String, fromTs: Option[Long], toTs: Option[Long], interval: Interval, agg: Aggregation, rawElements: Option[Int]): Future[Seq[Metric]] = {

    val metrics = store.getOrElse(source, mutable.Set[Metric]())
    val results = metrics filter { m =>
      m.timestamp < toTs.getOrElse(Long.MaxValue) && m.timestamp >= fromTs.getOrElse(0L)
    } groupBy { m =>
      val truncUs = interval match {
        case Moment => 1
        case Minute => 60 * 1000
        case Hour => 60 * 60 * 1000
        case Day => 24 * 60 * 60 * 1000
        case Week => 7 * 24 * 60 * 60 * 1000
        case Month => 30 * 24 * 60 * 60 * 1000
      }
      (m.timestamp / truncUs) * truncUs
    } map { case (ts, data) =>

      val vals: Seq[Double] = data.toSeq.map(_.value)
      data.head.copy(timestamp = ts, value = agg match {
        case Average =>
          val (s, l) = vals.foldLeft((0.0, 0)) { (t, r) => (t._1 + r, t._2 + 1) }
          s / l
        case Count => vals.size
        case Max => vals.max
        case Min => vals.min
        case Sum => vals.sum
        case Raw => vals.head
      })
    } toList

    Future.successful(
      rawElements match {
        case Some(limit) => results.sortBy(_.timestamp).takeRight(limit)
        case None => results.sortBy(_.timestamp)
      }
    )
  }

  override def dropWoof(woof: String): Future[Any] = {
    val sources = store.filter(_._2.exists(_.woof == woof)).keys
    store --= sources
    Future.unit
  }
}
