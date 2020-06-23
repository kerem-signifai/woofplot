package bootstrap

import gui._
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}

@Singleton
class GUIBootstrap @Inject()(
  config: Configuration,
) extends Logging {

  if (config.get[Boolean]("gui")) {
    logger.info("Initializing GUI")
    WoofGUI.initialize(config)
  } else {
    logger.info("Starting in headless mode")
  }
}
