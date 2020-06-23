package service.store

import model.Metric
import model.Query.{Aggregation, Interval}

import scala.concurrent.Future

trait MetricStore {
  def insertMetrics(metrics: Seq[Metric]): Future[Any]
  def dropWoof(woof: String): Future[Any]
  def queryMetrics(source: String, fromTs: Long, toTs: Long, interval: Interval, agg: Aggregation): Future[Seq[Metric]]
}
