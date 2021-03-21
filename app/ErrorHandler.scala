import javax.inject.Singleton
import org.apache.logging.log4j.scala.Logging
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent._

@Singleton
class ErrorHandler extends HttpErrorHandler with Logging {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    logger.error(s"Client error $statusCode occurred for ${request.method} ${request.target.uri} - $message")
    Future.successful(
      Status(statusCode)(message)
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"Server error occurred", exception)
    Future.successful(
      exception match {
        case _: IllegalArgumentException => BadRequest(exception.getMessage)
        case _ => InternalServerError(exception.getMessage)
      }
    )
  }
}
