import ReleaseTransformations._
import com.typesafe.config._
import com.typesafe.sbt.packager.docker.DockerChmodType
import play.sbt.PlayRunHook

import scala.sys.process.Process

val circeVersion = "0.12.3"
val slickVersion = "3.3.2"
val playCirceVersion = "2712.0"
val playSlickVersion = "4.0.2"
val flywayVersion = "6.0.3"
val log4j2Version = "2.11.1"
val log4jScalaVersion = "11.0"
val postgresVersion = "42.2.9"
val slickPGVersion = "0.18.1"
val jeroMQVersion = "0.5.1"

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
	releaseStepTask(publish in Docker in woofplot),
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

lazy val uiSrcDir = settingKey[File]("Location of UI project")
lazy val uiBuildDir = settingKey[File]("Location of UI build's managed resources")
lazy val uiDepsDir = settingKey[File]("Location of UI build dependencies")

lazy val uiClean = taskKey[Unit]("Clean UI build files")
lazy val uiTest = taskKey[Unit]("Run UI tests when testing application.")
lazy val uiStage = taskKey[Unit]("Run UI build when packaging the application.")

lazy val woofplot = (project in file("."))
	.enablePlugins(PlayScala, DockerPlugin, FlywayPlugin)
	.disablePlugins(PlayLogback)
	.settings(
		commonSettings,
		name := "woofplot",
		PlayKeys.playDefaultPort := 8080,
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
			"com.github.tminglei" %% "slick-pg" % slickPGVersion,

			"org.zeromq" % "jeromq" % jeroMQVersion,

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

		//		sourceGenerators in Compile += slickCodegen,
		//		slickCodegenDatabaseUrl := dbUrl,
		//		slickCodegenDatabaseUser := dbUser,
		//		slickCodegenDatabasePassword := dbPassword,
		//		slickCodegenOutputPackage := "dao.domain",
		//		slickCodegenIncludedTables := Seq("woof", "source"),
		//		slickCodegenDriver := slick.jdbc.MySQLProfile,
		//		slickCodegenJdbcDriver := "org.postgresql.Driver",

		uiSrcDir := baseDirectory.value / "ui",
		uiBuildDir := uiSrcDir.value / "build",
		uiDepsDir := uiSrcDir.value / "node_modules",

		uiClean := {
			IO.delete(uiBuildDir.value)
		},

		uiTest := {
			val dir = uiSrcDir.value
			if (!(uiDepsDir.value.exists() || runProcess("yarn install", dir)) || !runProcess("yarn run test", dir)) {
				throw new Exception("UI tests failed.")
			}
		},

		uiStage := {
			val dir = uiSrcDir.value
			if (!(uiDepsDir.value.exists() || runProcess("yarn install", dir)) || !runProcess("yarn run build", dir)) {
				throw new Exception("UI build failed.")
			}
		},

		dist := (dist dependsOn uiStage).value,
		stage := (stage dependsOn uiStage).value,
		test := ((test in Test) dependsOn uiTest).value,
		clean := (clean dependsOn uiClean).value,
		publishLocal in Docker := (publishLocal in Docker).dependsOn(uiStage).value,
		publish in Docker := (publish in Docker).dependsOn(uiStage).value,
		PlayKeys.playRunHooks += uiBuildHook(uiSrcDir.value),
		unmanagedResourceDirectories in Assets += uiBuildDir.value,

		dockerUpdateLatest := true,
		dockerExposedPorts += 8080,
		dockerChmodType := DockerChmodType.UserGroupWriteExecute,
		dockerBaseImage := "openjdk:8-jdk",
		dockerEntrypoint ++= Seq(
			"""-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager""",
			"""-Dplay.server.pidfile.path=/dev/null"""
		)
	)

def runProcess(script: String, dir: File): Boolean = {
	if (System.getProperty("os.name").toLowerCase().contains("win")) {
		Process("cmd /c set CI=true&&" + script, dir)
	} else {
		Process("env CI=true " + script, dir)
	}
}.! == 0


def uiBuildHook(uiSrc: File): PlayRunHook = {

	new PlayRunHook {

		var process: Option[Process] = None

		var install: String = "yarn install"
		var run: String = "yarn run start"

		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			install = "cmd /c" + install
			run = "cmd /c" + run
		}

		override def beforeStarted(): Unit = {
			Process(install, uiSrc).!
		}

		override def afterStarted(): Unit = {
			process = Some(
				Process(run, uiSrc).run
			)
		}

		override def afterStopped(): Unit = {
			process.foreach(p => p.destroy())
			process = None
		}

	}
}
