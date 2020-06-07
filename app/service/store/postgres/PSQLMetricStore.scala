package service.store.postgres

import java.sql.Timestamp

import javax.inject.Inject
import model.Metric
import model.Query.{Aggregation, Interval}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import service.store.MetricStore
import service.store.postgres.ExtendedPostgresProfile.api._
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}

class PSQLMetricStore @Inject()(
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends MetricStore with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

	implicit val getWoofResult: GetResult[Metric] = GetResult(r => Metric(r <<, r <<, r.nextTimestamp().getTime, r <<))

	override def insertMetrics(metrics: Seq[Metric]): Future[Any] = {
		Future {
			val session = db.createSession()
			val stmt = session.prepareStatement("INSERT INTO metrics VALUES(?, ?, ?, ?) ON CONFLICT DO NOTHING")
			metrics.foreach { metric =>
				stmt.setString(1, metric.source)
				stmt.setString(2, metric.woof)
				stmt.setTimestamp(3, new Timestamp(metric.timestamp))
				stmt.setDouble(4, metric.value)
				stmt.addBatch()
			}
			stmt.executeBatch()
			session.close()
		}
	}

	override def queryMetrics(source: String, fromTs: Long, toTs: Long, interval: Interval, agg: Aggregation): Future[Seq[Metric]] = {
		val from = new Timestamp(fromTs)
		val to = new Timestamp(toTs)
		db run sql"SELECT max(source), max(woof), date_trunc('#${interval.key}', timestamp), #${agg.fx}(value) FROM metrics WHERE source = $source AND timestamp >= $from AND timestamp < $to GROUP BY 3 ORDER BY 3".as[Metric]
	}
}
