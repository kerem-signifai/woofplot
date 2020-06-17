package bootstrap

import gui._
import javax.inject.{Inject, Singleton}
import javax.swing._
import play.api.{Configuration, Logging}
import java.awt.Desktop
import java.net.URI

@Singleton
class GUIBootstrap @Inject()(
  config: Configuration
) extends Logging {

  if (config.get[Boolean]("headless")) {
    logger.info("Starting in headless mode")
  } else {
    logger.info("Initializing GUI")
    SwingUtilities.invokeLater(WoofGUI)

    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(URI.create(s"http://localhost:${config.get[Int]("http.port")}"))
      } else {
        logger.info("BROWSE desktop action not supported")
      }
    } else {
      logger.info("Desktop not supported")
    }
  }
}
