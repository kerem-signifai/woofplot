package service

import akka.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MessageServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

	"A MessageService" should {
		"interact with woof" in {
			val cfg = Configuration.from(Map("zmq.timeout" -> "15.seconds"))
			val actor = ActorSystem("test")
			val msg = new MessageService(cfg, actor)

			val woof = "woof://128.111.45.83/mnt/monitor/office.download.wired"
//		val woof = "woof://128.111.45.83/mnt/monitor/office.upload.wired"
//		val woof = "woof://128.111.45.83/weathercat/goleta-home.wind"
//		val woof = "woof://128.111.45.83/mnt/elechome/elecdata-home"
			val fut = msg.fetch(woof, 3)

			val payloads = Await.result(fut, 30.seconds)

			payloads foreach { println(_) }

			val seqNo = Await.result(msg.getLatestSeqNo(woof), 30.seconds)
			val msgSize = Await.result(msg.getElementSize(woof), 30.seconds)

			println(s"seqNo: $seqNo, size: $msgSize")

		}
	}
}
