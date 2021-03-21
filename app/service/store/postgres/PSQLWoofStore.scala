package service.store.postgres

import javax.inject.Inject
import model.Query.{Conversion, Identity}
import model.{Woof, WoofBlueprint, Column}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import service.store.WoofStore
import service.store.postgres.ExtendedPostgresProfile.api._
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}

class PSQLWoofStore @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends WoofStore with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  private def toColumns(fields: Seq[Int], names: Seq[String], conversions: Seq[String]): Seq[Column] = {
    fields zip names zip conversions map { case ((field, name), conversion) =>
      Column(
        field,
        name,
        Conversion.find(conversion).getOrElse(Identity)
      )
    }
  }

  implicit val readWoof: GetResult[Woof] = GetResult(r => Woof(r <<, r <<, r <<, toColumns(r <<, r <<, r <<), r <<))

  override def insertWoof(woof: WoofBlueprint): Future[Woof] = {
    val fields = woof.columns.map(_.field)
    val columnNames = woof.columns.map(_.name)
    val conversions = woof.columns.map(_.conversion.key)
    db run sql"INSERT INTO woofs(url, name, fields, col_names, conversions) VALUES(${woof.url}, ${woof.name}, $fields, $columnNames, $conversions) RETURNING *".as[Woof].head
  }

  override def updateWoof(woofId: Long, woof: WoofBlueprint): Future[Woof] = {
    val fields = woof.columns.map(_.field)
    val columnNames = woof.columns.map(_.name)
    val conversions = woof.columns.map(_.conversion.key)
    db run sql"UPDATE woofs SET url=${woof.url}, name=${woof.name}, fields=$fields, col_names=$columnNames, conversions=$conversions WHERE woof_id=$woofId RETURNING *".as[Woof].head
  }

  override def fetchWoof(woofId: Long): Future[Option[Woof]] = {
    db run sql"SELECT * FROM woofs where woof_id=$woofId".as[Woof].headOption
  }

  override def listWoofs: Future[Seq[Woof]] = {
    db run sql"SELECT * FROM woofs ORDER BY updated_at".as[Woof]
  }

  override def updateWoofSeqNo(woofId: Long, latestSeqNo: Long): Future[Any] = {
    db run sqlu"UPDATE woofs SET latest_seq_no=$latestSeqNo WHERE woof_id=$woofId"
  }

  override def deleteWoof(woofId: Long): Future[Any] = {
    db run sqlu"DELETE FROM woofs WHERE woof_id=$woofId"
  }
}
