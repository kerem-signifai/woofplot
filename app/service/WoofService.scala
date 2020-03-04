package service

import dao.{SourceDAO, WoofDAO}
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.{Source, Woof}
import org.apache.logging.log4j.scala.Logging
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import Function.tupled

@Singleton
class WoofService @Inject()(
	config: Configuration,
	woofDAO: WoofDAO,
	sourceDAO: SourceDAO
)(implicit ec: ExecutionContext) extends Logging {

	def createSource(source: Source): Future[Any] = sourceDAO.insertSource(source)
	def listSources: Future[Seq[Source]] = sourceDAO.listSources
	def updateSource(id: String, source: Source): Future[Any] = sourceDAO.updateSource(id, source)
	def deleteSource(id: String): Future[Any] = sourceDAO.deleteSource(id)

	def queryWoofs(woofId: String, from: Long, to: Long, interval: Interval, agg: Aggregation): Future[Seq[Woof]] = woofDAO queryWoofs(woofId, from, to, interval, agg)

	def ingestWoof(sourceId: String, payload: String): Future[Seq[Woof]] = {
		listSources flatMap (_ find (_.id == sourceId) match {
			case Some(source) =>
				val pattern = source.pattern.r
				payload match {
					case pattern(groups@_*) =>
						Future sequence (groups zip source.datatypes map tupled { (value, dataType) =>
							val woof = Woof(s"${sourceId}_$dataType", (groups.last.toDouble * 1000).toLong, value.toDouble)
							woofDAO insertWoof (source, woof) map (_ => woof)
						})
					case p => throw new IllegalArgumentException(s"Unable to parse woof '$payload' using source $sourceId - $p")
				}
			case _ => throw new IllegalArgumentException(s"Unable to find woof source for source $sourceId")
		})
	}
}
