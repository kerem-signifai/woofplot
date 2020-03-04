package bootstrap

import javax.inject.{Inject, Singleton}
import org.flywaydb.core.api.configuration.FluentConfiguration
import play.api.Configuration

@Singleton
class SchemaMigrationBootstrap @Inject() (
	config: Configuration
) {
	private final val dbConfigRoot = "slick.dbs.default.db"

	private val dbConfig = config.get[Configuration](dbConfigRoot)
	private val url = dbConfig.get[String]("url")
	private val user = dbConfig.get[String]("user")
	private val password = dbConfig.get[String]("password")
	private val flywayConfig = new FluentConfiguration()
	flywayConfig
		.dataSource(url, user, password)
		.load()
		.migrate()
}
