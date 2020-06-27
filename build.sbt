import com.typesafe.config._
import com.typesafe.sbt.packager.docker.DockerChmodType
import play.sbt.PlayRunHook

import scala.sys.process.Process

val circeVersion = "0.12.3"
val slickVersion = "3.3.2"
val playCirceVersion = "2712.0"
val playSlickVersion = "4.0.2"
val flywayVersion = "6.4.4"
val flywayPlayVersion = "6.0.0"
val log4j2Version = "2.11.1"
val log4jScalaVersion = "12.0"
val postgresVersion = "42.2.9"
val slickPGVersion = "0.18.1"
val jeroMQVersion = "0.5.1"

name := "woofplot"
maintainer := "kerem@ucsb.edu"
organization := "edu.ucsb"
scalaVersion := "2.13.2"
resolvers ++= Seq[Resolver](
  Resolver.mavenLocal
)

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8"
)
scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-language:postfixOps"
)

enablePlugins(PlayScala, DockerPlugin, UniversalPlugin, JDKPackagerPlugin)
disablePlugins(PlayLogback)

jdkPackagerBasename := "WoofPlot"
jdkPackagerType := "dmg"
jdkPackagerProperties := Map(
  "apple.awt.application.name" -> "WoofPlot",
  "gui" -> "true"
)
name in JDKPackager := "WoofPlot"

PlayKeys.playDefaultPort := 8080

fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)
assemblyMergeStrategy in assembly := {
  case PathList("play", "reference-overrides.conf")  => MergeStrategy.concat
  case r if r.startsWith("reference.conf") => MergeStrategy.concat
  case PathList("META-INF", m) if m.equalsIgnoreCase("MANIFEST.MF") => MergeStrategy.discard
  case x => MergeStrategy.first
}

lazy val uiSrcDir = settingKey[File]("Location of UI project")
lazy val uiBuildDir = settingKey[File]("Location of UI build's managed resources")
lazy val uiDepsDir = settingKey[File]("Location of UI build dependencies")

lazy val uiClean = taskKey[Unit]("Clean UI build files")
lazy val uiTest = taskKey[Unit]("Run UI tests when testing application.")
lazy val uiStage = taskKey[Unit]("Run UI build when packaging the application.")

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-slick" % playSlickVersion,
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.dripower" %% "play-circe" % playCirceVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "org.flywaydb" % "flyway-core" % flywayVersion,
  "org.flywaydb" %% "flyway-play" % flywayPlayVersion,

  "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-jul" % log4j2Version,
  "org.apache.logging.log4j" %% "log4j-api-scala" % log4jScalaVersion,
  "org.postgresql" % "postgresql" % postgresVersion,
  "com.github.tminglei" %% "slick-pg" % slickPGVersion,

  "org.zeromq" % "jeromq" % jeroMQVersion,

  "org.mockito" % "mockito-core" % "3.3.3" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
)
excludeDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic",
  "ch.qos.logback" % "logback-core"
)
packageName := "woof-query"
routesImport += "model.Query._"

uiSrcDir := baseDirectory.value / "ui"
uiBuildDir := uiSrcDir.value / "build"
uiDepsDir := uiSrcDir.value / "node_modules"

uiClean := {
  IO.delete(uiBuildDir.value)
}

uiTest := {
  val dir = uiSrcDir.value
  if (!(uiDepsDir.value.exists() || runProcess("yarn install", dir)) || !runProcess("yarn run test", dir)) {
    throw new Exception("UI tests failed.")
  }
}

uiStage := {
  val dir = uiSrcDir.value
  if (!(uiDepsDir.value.exists() || runProcess("yarn install", dir)) || !runProcess("yarn run build", dir)) {
    throw new Exception("UI build failed.")
  }
}

dist := (dist dependsOn uiStage).value
test := ((test in Test) dependsOn uiTest).value
clean := (clean dependsOn uiClean).value
publishLocal in Docker := (publishLocal in Docker).dependsOn(uiStage).value
publish in Docker := (publish in Docker).dependsOn(uiStage).value
PlayKeys.playRunHooks += uiBuildHook(uiSrcDir.value)
unmanagedResourceDirectories in Assets += uiBuildDir.value
unmanagedResourceDirectories in Compile += uiBuildDir.value

dockerUpdateLatest := true
dockerExposedPorts += 8080
dockerChmodType := DockerChmodType.UserGroupWriteExecute
dockerBaseImage := "openjdk:14-jdk"
dockerEntrypoint ++= Seq(
  """-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager""",
  """-Dconfig.resource=postgres.conf"""
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
