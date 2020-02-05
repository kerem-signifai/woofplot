package config

import com.typesafe.config.Config
import play.api.ConfigLoader

import scala.collection.JavaConverters._

case class WoofConfig(id: String, name: String, dataType: String, url: String)

object WoofConfig {
	implicit val configLoader: ConfigLoader[Seq[WoofConfig]] = (rootConfig: Config, path: String) => {
		rootConfig.getConfigList(path).asScala.map(config =>
			WoofConfig(
				config.getString("id"),
				config.getString("name"),
				config.getString("dataType"),
				config.getString("url")
			))
	}
}
