package controllers

import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Query.{Aggregation, Interval}
import model.WoofBlueprint
import org.apache.logging.log4j.scala.Logging
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.WoofService
import model.Codec._

import scala.concurrent.ExecutionContext

@Singleton
class WoofController @Inject()(
  woofService: WoofService,
  authActions: AuthActions
)(implicit ec: ExecutionContext) extends InjectedController with Circe with Logging {

  def createWoof(): Action[WoofBlueprint] = authActions.admin.async(circe.json[WoofBlueprint]) { implicit request =>
    val payload = request.body
    logger.debug(s"Received request to create woof $payload")
    woofService.createWoof(payload) map { _ => Created }
  }

  def updateWoof(woofId: Long): Action[WoofBlueprint] = authActions.admin.async(circe.json[WoofBlueprint]) { implicit request =>
    val payload = request.body
    logger.debug(s"Received request to update woof $woofId to: $payload")
    woofService.updateWoof(woofId, payload) map { _ => NoContent }
  }

  def listWoofs(): Action[AnyContent] =
    Action async {
      logger.debug(s"Received request to list available woofs")
      woofService.listWoofs map { a => Ok(a.asJson) }
    }

  def deleteWoof(woofId: Long): Action[AnyContent] = authActions.admin.async {
    logger.debug(s"Received request to delete woof $woofId")
    woofService.deleteWoof(woofId) map { _ => NoContent }
  }

  def getRetentionPolicy: Action[AnyContent] = Action async {
    logger.debug(s"Received request to get metric retention policy")
    woofService.getRetentionPolicy map { _.map(_.toMillis) } map { a => Ok(a.asJson) }
  }

  def setRetentionPolicy(weeks: Int): Action[AnyContent] = authActions.admin.async {
    logger.debug(s"Received request to set metric retention policy to $weeks weeks")
    woofService.setRetentionPolicy(weeks) map { _ => Created }
  }

  def deleteRetentionPolicy(): Action[AnyContent] = authActions.admin.async {
    logger.debug(s"Received request to delete metric retention policy")
    woofService.deleteRetentionPolicy() map { _ => NoContent }
  }

  def queryWoofs(woofId: Long, field: Int, from: Option[Long], to: Option[Long], interval: Interval, aggregation: Aggregation, rawElements: Option[Int]): Action[AnyContent] =
    Action async {
      logger.info(s"Received request to query woof $woofId field $field in ($from:$to) using $aggregation over $interval interval; raw: $rawElements")
      woofService.queryWoofs(woofId, field, from, to, interval, aggregation, rawElements) map { a => Ok(a.asJson) }
    }

  def syncWoof(woofId: Long, history: Int): Action[AnyContent] = authActions.admin.async {
    logger.debug(s"Received request to synchronize woof $woofId with history: $history")
    woofService.fetchWoof(woofId) flatMap {
      case Some(woof) => woofService.syncWoof(woof, Some(history))
      case None => throw new IllegalArgumentException("Unable to find woof")
    } map { _ => Ok }
  }

  def peekWoof(woofUrl: String): Action[AnyContent] = Action async {
    logger.debug(s"Received request to peek woof at $woofUrl")
    woofService.peekWoof(woofUrl) map { a => Ok(a.asJson) }
  }
}
