package dao

import dao.ExtendedPostgresProfile.api._
import javax.inject.Inject
import model.Query.{Conversion, Identity}
import model.{Woof, WoofField}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.GetResult

import scala.Function.tupled
import scala.concurrent.{ExecutionContext, Future}

class WoofDAO @Inject()(
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] {

	private def getWoofFields(labels: Seq[String], conversions: Seq[String]): Seq[WoofField] = {
		labels zip conversions map tupled { (label, conversion) =>
			WoofField(
				label,
				Conversion.find(conversion) match {
					case Some(conv) => conv
					case _ => Identity
				}
			)
		}
	}

	implicit val getSourceResult: GetResult[Woof] = GetResult(r => Woof(r <<, r <<, r <<, getWoofFields(r <<, r <<)))

	def insertWoof(source: Woof): Future[Any] = {
		val labels = source.fields.map(_.label)
		val conversions = source.fields.map(_.conversion.key)
		db run sqlu"INSERT INTO woofs VALUES(${source.url}, ${source.name}, ${source.pattern}, ${labels}, ${conversions})"
	}

	def fetchWoof(url: String): Future[Option[Woof]] = {
		db run sql"SELECT * from woofs where url=${url}".as[Woof].headOption
	}

	def listWoofs: Future[Seq[Woof]] = {
		db run sql"SELECT * FROM woofs".as[Woof]
	}

	def updateWoof(url: String, source: Woof): Future[Any] = {
		val labels = source.fields.map(_.label)
		val conversions = source.fields.map(_.conversion.key)
		db run sqlu"UPDATE woofs SET name=${source.name}, pattern=${source.pattern}, labels=${labels}, conversions=${conversions} WHERE url=${url}"
	}

	def deleteWoof(url: String): Future[Any] = {
		db run sqlu"DELETE FROM woofs WHERE url=${url}"
	}
}
