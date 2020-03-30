package dao

import javax.inject.Inject
import model.Woof
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.GetResult
import ExtendedPostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class WoofDAO @Inject()(
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] {

	implicit val getSourceResult: GetResult[Woof] = GetResult(r => Woof(r <<, r <<, r <<, r <<))

	def insertWoof(source: Woof): Future[Any] = {
		db run sqlu"INSERT INTO woofs VALUES(${source.url}, ${source.name}, ${source.pattern}, ${source.dataLabels})"
	}

	def listWoofs: Future[Seq[Woof]] = {
		db run sql"SELECT * FROM woofs".as[Woof]
	}

	def updateWoof(url: String, source: Woof): Future[Any] = {
		db run sqlu"UPDATE woofs SET name=${source.name}, pattern=${source.pattern}, data_labels=${source.dataLabels} WHERE url=${url}"
	}

	def deleteWoof(url: String): Future[Any] = {
		db run sqlu"DELETE FROM woofs WHERE url=${url}"
	}
}
