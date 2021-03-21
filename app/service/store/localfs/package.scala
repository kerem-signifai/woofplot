package service.store

import java.io.{FileInputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.Locale
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import akka.actor.ActorSystem
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Using

package object localfs {

  abstract class FileBackedStore(resource: String)(implicit
    ec: ExecutionContext,
    config: Configuration,
    applicationLifecycle: ApplicationLifecycle,
    actorSystem: ActorSystem
  ) extends Logging {

    def serialize: String
    def deserialize(payload: String): Either[Throwable, Int]

    def initFSBackedStore(): Unit = {
      readSnapshot()

      val initialDelay = config.get[FiniteDuration](s"localfs.snapshot.$resource.initial_delay")
      val period = config.get[FiniteDuration](s"localfs.snapshot.$resource.period")

      val task = actorSystem.scheduler.scheduleWithFixedDelay(initialDelay, period)(() => writeSnapshot())
      applicationLifecycle addStopHook (() => Future {
        writeSnapshot()
        task.cancel()
      })
    }

    private def readSnapshot(): Unit = {
      logger.info(s"Reading $resource from disk")

      val sourceFile = dataDirectory.resolve(s"$resource.dat").toFile
      if (sourceFile.exists()) {

        Using.Manager { use =>
          val src = use(Source.fromInputStream(new GZIPInputStream(new FileInputStream(sourceFile))))
          deserialize(src.getLines().mkString("\n")) match {
            case Right(result) => logger.info(s"Read $result $resource from ${sourceFile.getAbsolutePath}")
            case Left(ex) => logger.error(s"Failed to read $resource from ${sourceFile.getAbsolutePath}", ex)
          }
        }

      } else {
        logger.info(s"Could not find file ${sourceFile.getAbsolutePath}")
      }
    }

    private def writeSnapshot(): Unit = {
      logger.info(s"Writing $resource to disk")

      val tmpDstPath = dataDirectory.resolve(s"$resource.dat.cur")
      val dstPath = dataDirectory.resolve(s"$resource.dat")

      Using.Manager { use =>
        val os = use(new GZIPOutputStream(new FileOutputStream(tmpDstPath.toFile)))
        os.write(serialize.getBytes)
        os.flush()
      }

      Files.move(tmpDstPath, dstPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      logger.info(s"Wrote $resource to ${dstPath.toFile.getAbsolutePath}")
    }
  }

  def dataDirectory: Path = {
    val dataDir = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH) match {
      case os if os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0 =>
        Paths.get(System.getProperty("user.home"), "Library", "Application Support", "WoofPlot")
      case os if os.indexOf("win") >= 0 =>
        Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "WoofPlot")
      case os if os.indexOf("nux") >= 0 =>
        Paths.get(System.getProperty("user.home"), ".woofplot")
      case _ => throw new IllegalArgumentException("Could not detect operating system")
    }
    dataDir.toFile.mkdirs()
    dataDir
  }
}
