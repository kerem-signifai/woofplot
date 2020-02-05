package controllers

import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.WoofService

import scala.concurrent.ExecutionContext

@Singleton
class WoofController @Inject() (
	woofService: WoofService
)(implicit ec: ExecutionContext) extends InjectedController with Circe {

	def listWoofs(): Action[AnyContent] =
		Action {
			Ok(woofService.woofs.asJson)
		}

	def queryWoofs(woofId: String, from: Long, to: Long, interval: Interval, aggregation: Aggregation): Action[AnyContent] =
		Action.async { implicit request =>
			woofService.queryWoofs(woofId, from, to, interval, aggregation).map(a => Ok(a.asJson))
		}
}
