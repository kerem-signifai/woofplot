import bootstrap.SchemaMigrationBootstrap
import com.google.inject.AbstractModule
import org.apache.logging.log4j.scala.Logging
import play.api.{Configuration, Environment, Play}
import service.store.memory.{MemoryMetricStore, MemoryWoofStore}
import service.store.postgres.{PSQLMetricStore, PSQLWoofStore}
import service.store.{MetricStore, WoofStore}

class Module(env: Environment, conf: Configuration) extends AbstractModule with Logging {

  override def configure(): Unit = {
    conf.getOptional[String]("store") match {
      case Some("memory") | None =>
        logger.info("Using in-memory storage backend")
        bind(classOf[MetricStore]).to(classOf[MemoryMetricStore]).asEagerSingleton()
        bind(classOf[WoofStore]).to(classOf[MemoryWoofStore]).asEagerSingleton()
      case Some("postgres") =>
        logger.info("Using PostreSQL storage backend")
        bind(classOf[SchemaMigrationBootstrap]).asEagerSingleton()
        bind(classOf[MetricStore]).to(classOf[PSQLMetricStore]).asEagerSingleton()
        bind(classOf[WoofStore]).to(classOf[PSQLWoofStore]).asEagerSingleton()
      case unknown@_ =>
        logger.error(s"Unknown storage type '${unknown}'")
        System.exit(1)
    }
  }
}
