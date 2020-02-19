package config

import com.typesafe.config.Config
import play.api.{ConfigLoader, Configuration}

import scala.collection.JavaConverters._

case class WoofSource(
	sourceId: String,
	name: String,
	pattern: String,
	dataTypes: Seq[String],
	url: String
)

object WoofSource {
	implicit val configLoader: ConfigLoader[Seq[WoofSource]] = (rootConfig: Config, path: String) => {
		rootConfig.getConfigList(path).asScala.map(Configuration(_)).map { config =>
			WoofSource(
				config get[String] "sourceId",
				config get[String] "name",
				config get[String] "pattern",
				config get[Seq[String]] "dataTypes",
				config get[String] "url"
			)
		}
	}
}
