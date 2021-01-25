package service.store.localfs

import akka.actor.ActorSystem
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.User
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import service.store.UserStore

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/*
  Not safe for production deployment: passwords are stored in plaintext.
  However, the inefficiencies of producing frequent snapshots of timeseries data
   and writing to a single file would likely bug an intended user more
 */
@Singleton
class FSUserStore @Inject()()(implicit
  ec: ExecutionContext,
  config: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
) extends FileBackedStore("users") with UserStore with Logging {
  private val users = new java.util.concurrent.ConcurrentHashMap[String, (String, User)]().asScala

  initFSBackedStore()
  if (!users.contains("admin")) {
    users += "admin" -> ("admin", User("admin", isAdmin = true))
  }

  override def serialize: String = users.asJson.toString()

  override def deserialize(payload: String): Either[Throwable, Int] = decode[Map[String, (String, User)]](payload) map { data =>
    data map { e => users.put(e._1, e._2) }
    users.size
  }

  override def getUser(username: String, password: String): Future[Option[User]] = Future.successful(
    users
      .find(e => e._1.equalsIgnoreCase(username) && e._2._1 == password)
      .map(_._2._2)
  )

  override def changePassword(username: String, newPassword: String): Future[Unit] = Future.successful(users.get(username) match {
    case Some(user) => users(username) = (newPassword, user._2)
    case _ =>
  })
}
