package dao

import java.sql.Timestamp

import dao.ExtendedPostgresProfile.api._
import javax.inject.Inject
import model.Query.{Aggregation, Interval}
import model.{Source, Woof}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}

class WoofDAO @Inject()(
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] {

	implicit val getWoofResult: GetResult[Woof] = GetResult(r => Woof(r <<, r.nextTimestamp().getTime, r <<))

	def insertWoof(source: Source, woof: Woof): Future[Any] = {
		val timestamp = new Timestamp(woof.timestamp)
		db run sqlu"INSERT INTO woof VALUES(${woof.id}, ${source.id}, ${timestamp}, ${woof.value})"
	}

	def queryWoofs(woofId: String, fromTs: Long, toTs: Long, interval: Interval, agg: Aggregation): Future[Seq[Woof]] = {
		val from = new Timestamp(fromTs)
		val to = new Timestamp(toTs)
		db run sql"SELECT max(id), date_trunc('#${interval.key}', timestamp), #${agg.fx}(value) FROM woof WHERE id = $woofId AND timestamp >= $from AND timestamp < $to GROUP BY 2 ORDER BY 2".as[Woof]
	}
}
