package service.store.localfs

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

import akka.actor.ActorSystem
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import model.{Woof, WoofBlueprint}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import service.store.WoofStore

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class FSWoofStore @Inject()()(implicit
  ec: ExecutionContext,
  config: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
) extends FileBackedStore("woofs") with WoofStore with Logging {
  private implicit val orderingById: Ordering[Woof] = Ordering.by(_.woofId)
  private val woofs = new java.util.concurrent.ConcurrentSkipListSet[Woof](orderingById).asScala
  private val woofIdGen = new AtomicLong
  private val updateLock = new ReentrantReadWriteLock

  initFSBackedStore()

  override def serialize: String = woofs.asJson.toString

  override def deserialize(payload: String): Either[Throwable, Int] = decode[Seq[Woof]](payload) map { data =>
    woofIdGen.set(math.max(woofIdGen.get(), data.map(_.woofId).maxOption.getOrElse(-1L) + 1))
    woofs ++= data
    data.size
  }

  override def updateWoof(woofId: Long, woof: WoofBlueprint): Future[Woof] = {
    fetchWoof(woofId) map {
      case Some(existing) =>
        val newWoof = existing.copy(url = woof.url, name = woof.name, columns = woof.columns)
        updateLock.writeLock().lock()
        try {
          woofs -= existing
          woofs += newWoof
          newWoof
        } finally {
          updateLock.writeLock().unlock()
        }
      case _ => throw new IllegalArgumentException(s"Unable to find woof $woofId")
    }
  }

  override def insertWoof(woof: WoofBlueprint): Future[Woof] = {
    val result = Woof(woofIdGen.getAndIncrement(), woof.url, woof.name, woof.columns, -1)
    woofs += result
    Future.successful(result)
  }

  override def fetchWoof(woofId: Long): Future[Option[Woof]] = {
    updateLock.readLock().lock()
    try {
      Future.successful(woofs.find(_.woofId == woofId))
    } finally {
      updateLock.readLock().unlock()
    }
  }

  override def listWoofs: Future[Seq[Woof]] = {
    updateLock.readLock().lock()
    try {
      Future.successful(woofs.toSeq)
    } finally {
      updateLock.readLock().unlock()
    }
  }

  override def updateWoofSeqNo(woofId: Long, latestSeqNo: Long): Future[Any] = {
    fetchWoof(woofId) map {
      case Some(found) =>
        updateLock.writeLock().lock()
        try {
          woofs -= found
          woofs += found.copy(latestSeqNo = latestSeqNo)
        } finally {
          updateLock.writeLock().unlock()
        }
      case _ =>
        logger.info(s"Failed to find woof $woofId")
    }
  }

  override def deleteWoof(woofId: Long): Future[Any] = {
    fetchWoof(woofId) map {
      case Some(found) => woofs -= found
      case _ => throw new IllegalArgumentException(s"Unable to find woof $woofId")
    }
  }
}
