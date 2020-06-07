package service.store

import model.Woof

import scala.concurrent.Future

trait WoofStore {
  def insertWoof(source: Woof): Future[Any]
  def fetchWoof(url: String): Future[Option[Woof]]
  def listWoofs: Future[Seq[Woof]]
  def updateWoofSeqNo(source: Woof, latestSeqNo: Long): Future[Any]
  def deleteWoof(url: String): Future[Any]
}
