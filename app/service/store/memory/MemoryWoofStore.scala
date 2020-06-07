package service.store.memory

import javax.inject.{Inject, Singleton}
import model.Woof
import play.api.Logging
import service.store.WoofStore

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class MemoryWoofStore @Inject()()(implicit ec: ExecutionContext) extends WoofStore with Logging {
  private val woofs = mutable.SortedSet[Woof]()(Ordering.by(_.url))

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
    Future.successful {
      Await.result(fetchWoof(url), Duration.Inf) match {
        case Some(found) => woofs.remove(found)
        case None => logger.info(s"Failed to find woof $url")
      }
    }
  }
}
