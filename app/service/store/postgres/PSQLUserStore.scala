package service.store.postgres

import java.security.SecureRandom
import java.util.Base64

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import model.User
import org.apache.logging.log4j.scala.Logging
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import service.store.UserStore
import service.store.postgres.ExtendedPostgresProfile.api._
import slick.jdbc.GetResult

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class PSQLUserStore @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends UserStore with HasDatabaseConfigProvider[ExtendedPostgresProfile] with Logging {
  private final val PBKDF2Iterations = 1024
  private final val HashBytes = 24
  private final val SaltBytes = 24

  private final val AdminPassword = "admin"

  private val rng = SecureRandom.getInstance("SHA1PRNG")
  private val base64e = Base64.getEncoder
  private val base64d = Base64.getDecoder

  case class UserRow(username: String, passwordHash: String, isAdmin: Boolean)

  implicit val getUserResult: GetResult[UserRow] = GetResult(r => UserRow(r <<, r <<, r <<))

  private val init = getUserRow("admin") flatMap {
    case Some(_) =>
      logger.info(s"Admin user already exists")
      Future.unit
    case _ =>
      logger.info(s"Admin user does not exist; creating now")
      val row = UserRow("admin", hashAndSalt(AdminPassword), isAdmin = true)
      addUser(row)
  }
  Await.result(init, Duration.Inf)

  private def pbkdf2(password: Array[Char], salt: Array[Byte], iterations: Int, bytes: Int) = {
    val spec = new PBEKeySpec(password, salt, iterations, bytes * 8)
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    skf.generateSecret(spec).getEncoded
  }

  private def hashAndSalt(plain: String) = {
    val salt = new Array[Byte](SaltBytes)
    rng.nextBytes(salt)

    val hash = pbkdf2(plain.toCharArray, salt, PBKDF2Iterations, HashBytes)
    s"$PBKDF2Iterations:${base64e.encodeToString(salt)}:${base64e.encodeToString(hash)}"
  }

  private def validatePassword(passwordHash: String, plain: String): Boolean = {
    passwordHash.split(":") match {
      case Array(maybeIters, saltStr, passStr) => maybeIters.toIntOption match {
        case Some(iters) =>
          val salt = base64d.decode(saltStr)
          val pass = base64d.decode(passStr)
          pbkdf2(plain.toCharArray, salt, iters, pass.length) sameElements pass
        case _ => false
      }
      case _ => false
    }
  }

  private def addUser(row: UserRow): Future[Int] = {
    db run sqlu"INSERT INTO users VALUES(${row.username}, ${row.passwordHash}, ${true})"
  }

  private def getUserRow(username: String): Future[Option[UserRow]] = {
    db run sql"SELECT * from users where username=${username}".as[UserRow].headOption
  }

  def getUser(username: String, password: String): Future[Option[User]] = {
    getUserRow(username) map {
      case Some(UserRow(username, passwordHash, isAdmin)) if validatePassword(passwordHash, password) => Some(User(username, isAdmin))
      case _ => None
    }
  }

  def changePassword(username: String, newPassword: String): Future[Unit] = {
    val hash = hashAndSalt(newPassword)
    db run sqlu"UPDATE users SET pass_hash=${hash} WHERE username=${username}".map(_ => ())
  }
}
