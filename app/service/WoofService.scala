package service

import config.WoofConfig
import dao.WoofDAO
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.Woof
import org.apache.logging.log4j.scala.Logging
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WoofService @Inject()(
	config: Configuration,
	woofDAO: WoofDAO
)(implicit ec: ExecutionContext) extends Logging {

	val woofs: Seq[WoofConfig] = config.get[Seq[WoofConfig]]("woofs")

	def queryWoofs(woofId: String, from: Long, to: Long, interval: Interval, agg: Aggregation): Future[Seq[Woof]] = woofDAO.queryWoofs(woofId, from, to, interval, agg)
}
