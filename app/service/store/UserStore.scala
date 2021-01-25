package service.store

import model.User

import scala.concurrent.Future

trait UserStore {
  def getUser(username: String, password: String): Future[Option[User]]
  def changePassword(username: String, newPassword: String): Future[Unit]
}
