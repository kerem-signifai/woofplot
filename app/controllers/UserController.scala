package controllers

import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.LoginRequest
import org.apache.logging.log4j.scala.Logging
import play.api.libs.circe.Circe
import play.api.mvc.{Action, AnyContent, InjectedController}
import service.LoginService

import scala.concurrent.ExecutionContext

@Singleton
class UserController @Inject()(
  loginService: LoginService,
  authActions: AuthActions
)(implicit ec: ExecutionContext) extends InjectedController with Circe with Logging {

  def login(): Action[LoginRequest] = Action.async(circe.json[LoginRequest]) { implicit request =>
    loginService.login(request.body).map(_.fold(Unauthorized.withNewSession)(t => Ok(t.asJson).withSession("token" -> t.token)))
  }

  def logout(): Action[AnyContent] = authActions.user { implicit request =>
    loginService.logout(request.token)
    Ok.withNewSession
  }

  def user(): Action[AnyContent] = authActions.user { implicit request =>
    Ok(request.user.asJson)
  }

  def changePassword(): Action[LoginRequest] = authActions.user.async(circe.json[LoginRequest]) { implicit request =>
    require(request.body.username == request.user.username)
    loginService.changePassword(request.body).map(_ => Ok)
  }
}
