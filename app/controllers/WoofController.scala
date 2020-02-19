package controllers

import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import org.apache.logging.log4j.scala.Logging
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.WoofService

import scala.concurrent.ExecutionContext

@Singleton
class WoofController @Inject()(
	woofService: WoofService
)(implicit ec: ExecutionContext) extends InjectedController with Circe with Logging {

	def listWoofs(): Action[AnyContent] =
		Action {
			logger.info(s"Received request to list available woof sources")
			Ok(woofService.woofs.asJson)
		}

	def ingestWoof(sourceId: String): Action[String] = Action.async(parse.text) { implicit request =>
		val payload = request.body
		logger.info(s"Ingesting woof for source $sourceId; payload: '$payload'")
		woofService.ingestWoof(sourceId, payload) map (a => Created(a.asJson))
	}

	def queryWoofs(woofId: String, from: Long, to: Long, interval: Interval, aggregation: Aggregation): Action[AnyContent] =
		Action async { implicit request =>
			logger.info(s"Received request to query woof $woofId in ($from:$to) using $aggregation over $interval interval")
			woofService.queryWoofs(woofId, from, to, interval, aggregation) map (a => Ok(a.asJson))
		}
}
