import bootstrap._
import com.google.inject.AbstractModule
import org.apache.logging.log4j.scala.Logging
import play.api.{Configuration, Environment}
import service.store.localfs.{FSMetricStore, FSUserStore, FSWoofStore}
import service.store.postgres.{PSQLMetricStore, PSQLUserStore, PSQLWoofStore}
import service.store.{MetricStore, UserStore, WoofStore}

class Module(env: Environment, conf: Configuration) extends AbstractModule with Logging {

  override def configure(): Unit = {
    bind(classOf[GUIBootstrap]).asEagerSingleton()
    conf.getOptional[String]("store") match {
      case Some("localfs") | None =>
        logger.info("Using file-backed memory storage backend")
        bind(classOf[MetricStore]).to(classOf[FSMetricStore]).asEagerSingleton()
        bind(classOf[WoofStore]).to(classOf[FSWoofStore]).asEagerSingleton()
        bind(classOf[UserStore]).to(classOf[FSUserStore]).asEagerSingleton()
      case Some("postgres") =>
        logger.info("Using PostreSQL storage backend")
        bind(classOf[MetricStore]).to(classOf[PSQLMetricStore]).asEagerSingleton()
        bind(classOf[WoofStore]).to(classOf[PSQLWoofStore]).asEagerSingleton()
        bind(classOf[UserStore]).to(classOf[PSQLUserStore]).asEagerSingleton()
      case unknown@_ =>
        logger.error(s"Unknown storage type '${unknown}'")
        System.exit(1)
    }
  }
}
