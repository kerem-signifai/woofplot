package service.store

import model.Metric
import model.Query.{Aggregation, Interval}

import scala.concurrent.Future

trait MetricStore {
  def insertMetrics(metrics: Seq[Metric]): Future[Any]
  def dropWoof(woof: String): Future[Any]
  def queryMetrics(source: String, fromTs: Option[Long], toTs: Option[Long], interval: Interval, agg: Aggregation, rawElements: Option[Int]): Future[Seq[Metric]]
}
