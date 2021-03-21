package service.store.postgres

import java.sql.Timestamp
import java.time.Duration

import javax.inject.Inject
import model.Metric
import model.Query._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import service.store.MetricStore
import service.store.postgres.ExtendedPostgresProfile.api._
import slick.jdbc.GetResult
import scala.jdk.DurationConverters._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class PSQLMetricStore @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends MetricStore with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  private final val MaxPsqlTimestamp = 1791740595661000L

  implicit val getWoofResult: GetResult[Metric] = GetResult(r => Metric(r <<, r <<, r.nextTimestamp().getTime, r <<))

  override def getRetentionPolicy: Future[Option[FiniteDuration]] = {
    db run sql"SELECT (config::json->'drop_after')::text::interval FROM _timescaledb_config.bgw_job WHERE proc_name='policy_retention'".as[Duration].headOption.map(_.map(_.toScala))
  }

  override def setRetentionPolicy(weeks: Int): Future[Any] = {
    deleteRetentionPolicy() flatMap { _ =>
      db run sql"SELECT add_retention_policy('metrics', INTERVAL '#$weeks WEEKS')".as[Int]
    }
  }

  override def deleteRetentionPolicy(): Future[Any] = {
    db run sql"SELECT remove_retention_policy('metrics', TRUE)".as[Int]
  }

  override def insertMetrics(metrics: Seq[Metric]): Future[Any] = {
    db run {
      SimpleDBIO[Unit] { session =>
        Using(session.connection.prepareStatement("INSERT INTO metrics VALUES(?, ?, ?, ?) ON CONFLICT DO NOTHING")) { stmt =>
          metrics.foreach { metric =>
            stmt.setLong(1, metric.woofId)
            stmt.setInt(2, metric.field)
            stmt.setTimestamp(3, new Timestamp(metric.timestamp))
            stmt.setDouble(4, metric.value)
            stmt.addBatch()
          }
          stmt.executeBatch()
        }
      }
    }
  }

  override def queryMetrics(woofId: Long, field: Int, fromTs: Option[Long], toTs: Option[Long], interval: Interval, agg: Aggregation, rawElements: Option[Int]): Future[Seq[Metric]] = {
    val from = new Timestamp(fromTs.getOrElse(0L))
    val to = new Timestamp(toTs.getOrElse(MaxPsqlTimestamp))
    val limit = rawElements.map(i => s"LIMIT $i").getOrElse("")
    val intervalKey = interval match {
      case Moment => "microsecond"
      case Minute => "minute"
      case Hour => "hour"
      case Day => "day"
      case Week => "week"
      case Month => "month"
    }
    val aggFx = agg match {
      case Raw => "MAX"
      case Average => "AVG"
      case Count => "COUNT"
      case Max => "MAX"
      case Min => "MIN"
      case Sum => "SUM"
    }
    db run sql"""
      SELECT max(woof_id), max(field), date_trunc('#$intervalKey', timestamp), #$aggFx(value)
      FROM metrics
      WHERE woof_id = $woofId AND field = $field AND timestamp >= $from AND timestamp < $to GROUP BY 3 ORDER BY 3 DESC
      #${limit}
    """.as[Metric].map(_.reverse)
  }

  override def dropWoof(woofId: Long): Future[Any] = {
    db run sqlu"DELETE FROM metrics WHERE woof_id = $woofId"
  }

  override def dropField(woofId: Long, field: Int): Future[Any] = {
    db run sqlu"DELETE FROM metrics WHERE woof_id = $woofId AND field = $field"
  }
}
