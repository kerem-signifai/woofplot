package service

import config.WoofSource
import dao.WoofDAO
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.Woof
import org.apache.logging.log4j.scala.Logging
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import Function.tupled

@Singleton
class WoofService @Inject()(
	config: Configuration,
	woofDAO: WoofDAO
)(implicit ec: ExecutionContext) extends Logging {

	val woofs: Seq[WoofSource] = config get[Seq[WoofSource]] "woofs"

	def queryWoofs(woofId: String, from: Long, to: Long, interval: Interval, agg: Aggregation): Future[Seq[Woof]] = woofDAO queryWoofs(woofId, from, to, interval, agg)

	def ingestWoof(sourceId: String, payload: String): Future[Seq[Woof]] = {
		woofs find (_.sourceId == sourceId) match {
			case Some(woofConfig) =>
				val pattern = woofConfig.pattern.r
				payload match {
					case pattern(groups@_*) =>
						Future sequence (groups zip woofConfig.dataTypes map tupled { (value, dataType) =>
							val woof = Woof(s"${sourceId}_$dataType", (groups.last.toDouble * 1000).toLong, value.toDouble)
							woofDAO insertWoof woof map (_ => woof)
						})
					case p => throw new IllegalArgumentException(s"Unable to parse woof '$payload' using source $sourceId - $p")
				}
			case _ => throw new IllegalArgumentException(s"Unable to find woof source for source $sourceId")
		}
	}
}
