package service.store.memory

import javax.inject.{Inject, Singleton}
import model.Metric
import model.Query._
import play.api.Logging
import service.store.MetricStore

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MemoryMetricStore @Inject()()(implicit ec: ExecutionContext) extends MetricStore with Logging {
  private val store: mutable.Map[String, mutable.Set[Metric]] = mutable.Map()
  private implicit val orderingByTs: Ordering[Metric] = Ordering.by(e => e.timestamp)

  override def insertMetrics(metrics: Seq[Metric]): Future[Any] = {
    metrics.groupBy(_.source) foreach {
      case (source, data) =>
        val sorted = store.getOrElseUpdate(source, mutable.SortedSet[Metric]())
        sorted ++= data
    }
    Future.unit
  }

  override def queryMetrics(source: String, fromTs: Long, toTs: Long, interval: Interval, agg: Aggregation): Future[Seq[Metric]] = {

    val metrics = store.getOrElse(source, mutable.Set[Metric]())
    val results = metrics filter { m =>
      m.timestamp < toTs && m.timestamp >= fromTs
    } groupBy { m =>
      val trunc = interval match {
        case Minute => 60 * 1000
        case Hour => 60 * 60 * 1000
        case Day => 24 * 60 * 60 * 1000
        case Week => 7 * 24 * 60 * 60 * 1000
        case Month => 30 * 24 * 60 * 60 * 1000
      }
      (m.timestamp / trunc) * trunc
    } map { case (ts, data) =>

      val vals: Seq[Double] = data.toSeq.map(_.value)
      data.head.copy(timestamp = ts, value = agg match {
        case Average =>
          val (s, l) = vals.foldLeft(0.0, 0) { (t, r) => (t._1 + r, t._2 + 1) }
          s / l
        case Count => vals.size
        case Max => vals.max
        case Min => vals.min
        case Sum => vals.sum
      })
    } toList

    Future.successful(results.sortBy(_.timestamp))
  }
}
