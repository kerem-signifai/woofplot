package controllers

import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.{SensorPayload, SensorType, Woof}
import org.apache.logging.log4j.scala.Logging
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.WoofService

import scala.concurrent.ExecutionContext

@Singleton
class WoofController @Inject()(
	woofService: WoofService
)(implicit ec: ExecutionContext) extends InjectedController with Circe with Logging {

	implicit val sensorTypeDec: Encoder[SensorType] = Encoder.encodeString.contramap(_.name())

	def createSource(): Action[Woof] = Action.async(circe.json[Woof]) { implicit request =>
		val payload = request.body
		logger.debug(s"Received request to create source $payload")
		woofService createWoof payload map (_ => Created)
	}

	def listSources(): Action[AnyContent] =
		Action async {
			logger.debug(s"Received request to list available woof sources")
			woofService.listWoofs map (a => Ok(a.asJson))
		}

	def updateSource(sourceId: String): Action[Woof] = Action.async(circe.json[Woof]) { implicit request =>
		val payload = request.body
		logger.debug(s"Received request to update source $sourceId to $payload")
		woofService updateWoof(sourceId, payload) map (_ => NoContent)
	}

	def deleteSource(sourceId: String): Action[AnyContent] = Action.async {
		logger.debug(s"Received request to delete source $sourceId")
		woofService deleteWoof sourceId map (_ => NoContent)
	}

	def queryWoofs(source: String, from: Long, to: Long, interval: Interval, aggregation: Aggregation): Action[AnyContent] =
		Action async {
			logger.debug(s"Received request to query source $source in ($from:$to) using $aggregation over $interval interval")
			woofService queryWoofs(source, from, to, interval, aggregation) map (a => Ok(a.asJson))
		}

	def syncSource(source: String, history: Int): Action[AnyContent] = Action.async {
		logger.debug(s"Received request to synchronize source $source with history: $history")
		woofService.fetchWoof(source).flatMap {
			case Some(woof) => woofService.syncWoof(woof, history, force = true)
			case None => throw new IllegalArgumentException("Unable to find woof")
		}.map(_ => Ok)
	}

	def peekWoof(source: String): Action[AnyContent] = Action async {
		logger.debug("Received request to peek woof $source")
		woofService peekWoof source map (a => Ok(a.asJson))
	}
}
