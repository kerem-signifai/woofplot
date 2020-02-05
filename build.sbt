import ReleaseTransformations._
import com.typesafe.config._
import com.typesafe.sbt.packager.docker.DockerChmodType

val circeVersion = "0.12.3"
val slickVersion = "3.3.2"
val playCirceVersion = "2712.0"
val playSlickVersion = "4.0.2"
val flywayVersion = "6.0.3"
val log4j2Version = "2.11.1"
val log4jScalaVersion = "11.0"
val postgresVersion = "42.2.9"

val deploy = taskKey[Unit]("Kubernetes deployment task")

deploy := {
	val dockerFullPath = (Docker / dockerAlias).value
	val k8sDeployment = sys.props.getOrElse("deployKubernetesPackageName", packageName.value)
	val k8sTemplatePath = sys.props.getOrElse("deployKubernetesTemplatePath", "kubernetes")
	val definitionDir = baseDirectory.value / k8sTemplatePath
	val templateValues = Map(
		"image" -> dockerFullPath
	)
	val stream = streams.value
	val kubectl = Deckhand.kubectl(stream.log)
	kubectl.setCurrentNamespace("default")

	if (definitionDir.exists && definitionDir.isDirectory) {
		val hostFiles = definitionDir.listFiles.filter(_.isFile)
		hostFiles.foreach { file =>
			stream.log.info(s"Deploying $k8sDeployment from file ${file.name}")
			kubectl.apply(Deckhand.mustache(file), templateValues)
		}
	} else {
		stream.log.warn(s"No definition files for $k8sDeployment in $definitionDir")
	}
	stream.log.info(s"Register $dockerFullPath, deploy $k8sDeployment from $definitionDir")
}

releaseIgnoreUntrackedFiles := true


// val releaseRepo = // do this
// publishMavenStyle := true
// publishTo in ThisBuild := Some(releaseRepo)

releaseProcess := Seq[ReleaseStep](
	checkSnapshotDependencies,
	inquireVersions,
	runClean,
	runTest,
	setReleaseVersion,
	commitReleaseVersion,
	tagRelease,
	publishArtifacts,
	releaseStepTask(publish in Docker in woofQuery),
	releaseStepTask(deploy in woofQuery),
	setNextVersion,
	commitNextVersion,
	pushChanges
)

releaseCommitMessage += " [skip ci]"

val appConfig = ConfigFactory.parseFile(new File("conf/application.conf"))
val config = ConfigFactory.load(appConfig)
val dbConfig = config.getConfig("slick.dbs.default.db")
val dbUrl = dbConfig.getString("url")
val dbUser = dbConfig.getString("user")
val dbPassword = dbConfig.getString("password")

val commonSettings = Seq(
	organization := "edu.ucsb",
	scalaVersion := "2.12.10",
	resolvers ++= Seq[Resolver](
		Resolver.mavenLocal
	)
)

lazy val woofQuery = (project in file("."))
	.enablePlugins(PlayScala, DockerPlugin, FlywayPlugin)
	.disablePlugins(PlayLogback)
	.settings(
		commonSettings,
		name := "woofplot",
		libraryDependencies ++= Seq(
			guice,
			"com.typesafe.play" %% "play-slick" % playSlickVersion,
			"com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
			"com.typesafe.slick" %% "slick" % slickVersion,
			"com.typesafe.slick" %% "slick-codegen" % slickVersion,
			"com.dripower" %% "play-circe" % playCirceVersion,
			"io.circe" %% "circe-core" % circeVersion,
			"io.circe" %% "circe-generic" % circeVersion,
			"io.circe" %% "circe-parser" % circeVersion,
			"org.flywaydb" % "flyway-core" % flywayVersion,

			"org.apache.logging.log4j" % "log4j-api" % log4j2Version,
			"org.apache.logging.log4j" % "log4j-core" % log4j2Version,
			"org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
			"org.apache.logging.log4j" % "log4j-jul" % log4j2Version,
			"org.apache.logging.log4j" %% "log4j-api-scala" % log4jScalaVersion,
			"org.postgresql" % "postgresql" % postgresVersion,

			"org.mockito" % "mockito-core" % "3.0.0" % Test,
			"org.scalatestplus.play" %% "scalatestplus-play" % "4.0.0" % Test
		),
		excludeDependencies ++= Seq(
			"ch.qos.logback" % "logback-classic",
			"ch.qos.logback" % "logback-core"
		),
		packageName := "woof-query",
		routesImport += "model.Query._",

		flywayUrl := dbUrl,
		flywayUser := dbUser,
		flywayPassword := dbPassword,
		flywayLocations := Seq("filesystem:conf/db/migration/default"),
		
		dockerChmodType := DockerChmodType.UserGroupWriteExecute,
		dockerRepository := Some("gcr.io"),
		dockerUsername := Some("/dev/null"),
		dockerBaseImage := "openjdk:8-jdk",
		dockerEntrypoint ++= Seq(
			"""-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager""",
			"""-Dplay.server.pidfile.path=/dev/null"""
		)
	)