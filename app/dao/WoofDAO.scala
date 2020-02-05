package dao

import java.sql.Timestamp

import javax.inject.Inject
import model.Query.{Aggregation, Interval}
import model.Woof
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.{GetResult, PostgresProfile}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class WoofDAO @Inject() (
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[PostgresProfile] {

	implicit val getWoofResult: GetResult[Woof] = GetResult(r => Woof(r <<, r.nextTimestamp().getTime, r <<))

	def queryWoofs(woofId: String, fromTs: Long, toTs: Long, interval: Interval, agg: Aggregation): Future[Seq[Woof]] = {
		val from = new Timestamp(fromTs)
		val to = new Timestamp(toTs)
		db.run(sql"SELECT max(id), date_trunc('#${interval.key}', timestamp), #${agg.fx}(value) from woofs WHERE id = $woofId AND timestamp >= $from and timestamp < $to GROUP BY 2 ORDER BY 2".as[Woof])
	}
}
