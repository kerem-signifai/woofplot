package controllers

import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.Source
import org.apache.logging.log4j.scala.Logging
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.WoofService

import scala.concurrent.ExecutionContext

@Singleton
class WoofController @Inject()(
	woofService: WoofService
)(implicit ec: ExecutionContext) extends InjectedController with Circe with Logging {

	def createSource(): Action[Source] = Action.async(circe.json[Source]) { implicit request =>
		val payload = request.body
		logger.info(s"Received request to create source $payload")
		woofService createSource payload map (_ => Created)
	}

	def listSources(): Action[AnyContent] =
		Action async {
			logger.info(s"Received request to list available woof sources")
			woofService.listSources map (a => Ok(a.asJson))
		}

	def updateSource(sourceId: String): Action[Source] = Action.async(circe.json[Source]) { implicit request =>
		val payload = request.body
		logger.info(s"Received request to update source $sourceId to $payload")
		woofService updateSource(sourceId, payload) map (_ => NoContent)
	}

	def deleteSource(sourceId: String): Action[AnyContent] = Action.async {
		logger.info(s"Received request to delete source $sourceId")
		woofService deleteSource sourceId map (_ => NoContent)
	}

	def ingestWoof(sourceId: String): Action[String] = Action.async(parse.text) { implicit request =>
		val payload = request.body
		logger.info(s"Ingesting woof for source $sourceId; payload: '$payload'")
		woofService ingestWoof(sourceId, payload) map (a => Created(a.asJson))
	}

	def queryWoofs(sourceId: String, from: Long, to: Long, interval: Interval, aggregation: Aggregation): Action[AnyContent] =
		Action async {
			logger.info(s"Received request to query source $sourceId in ($from:$to) using $aggregation over $interval interval")
			woofService queryWoofs(sourceId, from, to, interval, aggregation) map (a => Ok(a.asJson))
		}
}
