import akka.actor.Scheduler
import akka.pattern.after

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

package object service {
	private final val RETRY_DELAY = 1 second
	private final val RETRY_COUNT = 3

	def retry[T](op: => T)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = retry(RETRY_DELAY, RETRY_COUNT)(op)

	def retry[T](delay: FiniteDuration, retries: Int)(op: => T)(implicit ec: ExecutionContext, s: Scheduler): Future[T] =
		Future(op) recoverWith { case _ if retries > 0 => after(delay, s)(retry(delay, retries - 1)(op)) }
}
