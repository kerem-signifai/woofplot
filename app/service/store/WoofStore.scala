package service.store

import model.{Woof, WoofBlueprint}

import scala.concurrent.Future

trait WoofStore {
  def insertWoof(woof: WoofBlueprint): Future[Woof]
  def updateWoof(woofId: Long, woof: WoofBlueprint): Future[Woof]
  def fetchWoof(woofId: Long): Future[Option[Woof]]
  def listWoofs: Future[Seq[Woof]]
  def updateWoofSeqNo(woofId: Long, latestSeqNo: Long): Future[Any]
  def deleteWoof(woofId: Long): Future[Any]
}
