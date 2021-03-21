package service

import java.util.UUID

import javax.inject.{Inject, Singleton}
import model.{LoginRequest, LoginResponse, User}
import service.store.UserStore

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class LoginService @Inject()(
  userDAO: UserStore
)(implicit ec: ExecutionContext) {
  private val sessions = new java.util.concurrent.ConcurrentHashMap[String, User]().asScala

  def login(req: LoginRequest): Future[Option[LoginResponse]] = {
    userDAO.getUser(req.username, req.password) map {
      case Some(user) =>
        val token = s"woof-token-${UUID.randomUUID().toString}"
        sessions += token -> user
        Some(LoginResponse(user.username, user.isAdmin, token))
      case _ => None
    }
  }

  def changePassword(req: LoginRequest): Future[Unit] = userDAO.changePassword(req.username, req.password)

  def getSession(token: String): Option[LoginResponse] = {
    sessions.get(token).map(u => LoginResponse(u.username, u.isAdmin, token))
  }

  def logout(token: String): Unit = {
    sessions -= token
  }

}
