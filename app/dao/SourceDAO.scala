package dao

import javax.inject.Inject
import model.Source
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.GetResult
import ExtendedPostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class SourceDAO @Inject()(
	val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] {

	implicit val getSourceResult: GetResult[Source] = GetResult(r => Source(r <<, r <<, r <<, r <<, r <<))

	def insertSource(source: Source): Future[Any] = {
		db run sqlu"INSERT INTO source VALUES(${source.id}, ${source.name}, ${source.pattern}, ${source.datatypes}, ${source.url})"
	}

	def listSources: Future[Seq[Source]] = {
		db run sql"SELECT * FROM source".as[Source]
	}

	def updateSource(id: String, source: Source): Future[Any] = {
		db run sqlu"UPDATE source SET name=${source.name}, pattern=${source.pattern}, datatypes=${source.datatypes}, url=${source.url} WHERE id=${id}"
	}

	def deleteSource(id: String): Future[Any] = {
		db run sqlu"DELETE FROM source WHERE id=${id}"
	}
}
