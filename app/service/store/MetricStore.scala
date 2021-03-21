package service.store

import model.Metric
import model.Query.{Aggregation, Interval}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait MetricStore {
  def insertMetrics(metrics: Seq[Metric]): Future[Any]
  def dropWoof(woofId: Long): Future[Any]
  def dropField(woofId: Long, field: Int): Future[Any]
  def queryMetrics(woofId: Long, field: Int, fromTs: Option[Long], toTs: Option[Long], interval: Interval, agg: Aggregation, rawElements: Option[Int]): Future[Seq[Metric]]
  def getRetentionPolicy: Future[Option[FiniteDuration]]
  def setRetentionPolicy(weeks: Int): Future[Any]
  def deleteRetentionPolicy(): Future[Any]
}
