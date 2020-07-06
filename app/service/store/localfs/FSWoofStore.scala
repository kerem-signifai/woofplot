package service.store.localfs

import akka.actor.ActorSystem
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.Codec._
import model.Woof
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import service.store.WoofStore

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class FSWoofStore @Inject()()(implicit
  ec: ExecutionContext,
  config: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
) extends FileBackedStore("woofs") with WoofStore with Logging {
  private implicit val orderingByUrl: Ordering[Woof] = Ordering.by(_.url)
  private val woofs = new java.util.concurrent.ConcurrentSkipListSet[Woof](orderingByUrl).asScala

  initFSBackedStore()

  override def serialize: String = woofs.asJson.toString

  override def deserialize(payload: String): Either[Throwable, Int] = decode[Seq[Woof]](payload) map { data =>
    Await.result(Future.sequence(data map insertWoof), Duration.Inf).size
  }

  override def insertWoof(source: Woof): Future[Any] = {
    Future.successful(woofs += source)
  }

  override def fetchWoof(url: String): Future[Option[Woof]] = {
    Future.successful(woofs.find(_.url == url))
  }

  override def listWoofs: Future[Seq[Woof]] = {
    Future.successful(woofs.toSeq)
  }

  override def updateWoofSeqNo(source: Woof, latestSeqNo: Long): Future[Any] = {
    woofs synchronized {
      val woof = Await.result(fetchWoof(source.url), Duration.Inf)
      deleteWoof(source.url)
      woof match {
        case Some(found) => insertWoof(found.copy(latestSeqNo = latestSeqNo))
        case None => logger.info(s"Failed to find woof ${source.url}")
      }
    }
    Future.unit
  }

  override def deleteWoof(url: String): Future[Any] = {
    fetchWoof(url) map {
      case Some(found) => woofs.remove(found)
      case None => logger.info(s"Failed to find woof $url")
    }
  }
}
