package service

import java.io.{BufferedReader, ByteArrayInputStream, DataInputStream, InputStreamReader}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

import dao.{WoofDAO, MetricDAO}
import model.{Metric, SensorPayload, SensorType, Woof}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.zeromq.{SocketType, ZContext, ZMQ, ZMsg}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class WoofServiceTest extends PlaySpec with MockitoSugar with ScalaFutures {

	private final val WOOF_MSG_GET_EL_SIZE = 2
	private final val WOOF_MSG_GET = 3
	private final val WOOF_MSG_GET_LATEST_SEQNO = 5

	def hash(namespace: String): Long = {
		var h : BigInt = 5381
		val a: Long = 33

		for (c <- namespace) {
			h = ((h * a) + c.toInt) % ((2 * BigInt(Long.MaxValue + 1))) /* no mod p due to wrap */
		}

		/*
		 * hash namespace to port number between 50000 and 60000
		 */
		return (50000 + (h % 10000)).toLong
	}

	def tests(hex: String): Unit = {
		val longHex = parseUnsignedHex(hex)
		val d = java.lang.Double.longBitsToDouble(longHex)
		println(d)
	}

	def parseUnsignedHex(text: String): Long = {
		if (text.length == 16) return (parseUnsignedHex(text.substring(0, 1)) << 60) | parseUnsignedHex(text.substring(1))
		java.lang.Long.parseLong(text, 16)
	}

	"A WoofService" should {

		"msg" in {
//			val resource = "/fluxstatus-ptemp"
//			val namespace = "/lrec_flux_ns"

//			val resource = "/davis6163-iss3"
//			val namespace = "/davisstations"

//			val resource = "/goleta-home.wind"
//			val namespace = "/weathercat"

			val namespace = "/ediblecampus"
			val resource =  "/davis6163"

			val ip = "tcp://128.111.45.61"
			val woof = s"woof://$ip/$namespace/$resource"
			val port = (namespace.foldLeft(BigInt(5381L)) { (i, c) => (c + (i * 33L)) % (2 * BigInt(Long.MaxValue + 1)) } % 10000L) + 50000L

			println(s"$ip:$port")

			val ctx = new ZContext()
			val sock = ctx.createSocket(SocketType.REQ)
			sock.setReceiveTimeOut(5000)
			sock.connect(ip + ":" + port)

			def dispatch(msg: ZMsg) = {
				if (msg.send(sock)) {
					ByteBuffer.wrap(ZMsg.recvMsg(sock).getFirst.getData)
				} else {
					throw new RuntimeException("Failed to send message")
				}
			}

			val elementSizeMsg = new ZMsg()
			elementSizeMsg.addString(WOOF_MSG_GET_EL_SIZE.toString)
			elementSizeMsg.addString(woof)

			val elementSize = StandardCharsets.UTF_8.decode(dispatch(elementSizeMsg)).toString.toInt
			println(s"Element size: $elementSize")

			val latestSeqNoMsg = new ZMsg()
			latestSeqNoMsg.addString(WOOF_MSG_GET_LATEST_SEQNO.toString)
			latestSeqNoMsg.addString(woof)

			val latestSeqNo = StandardCharsets.UTF_8.decode(dispatch(latestSeqNoMsg)).toString.toInt
			println(s"Latest seq no: $latestSeqNo")

			val woofGetMsg = new ZMsg()
			woofGetMsg.addString(WOOF_MSG_GET.toString)
			woofGetMsg.addString(woof)
			woofGetMsg.addString(latestSeqNo.toString)

			val woofData = dispatch(woofGetMsg)
			val typ = woofData.get().toChar

			woofData.position(16)
			val ipBuf = woofData.slice()
			ipBuf.limit(25)
			val resIp = StandardCharsets.UTF_8.decode(ipBuf).toString

			woofData.position(44)
			val tvSec = woofData.getInt()
			val tvuSec = woofData.getInt()

			woofData.position(60)
			val payloadBuf = woofData.slice()
//			payloadBuf.array().foreach(println)
//			println(s"0 found at ${payloadBuf.asCharBuffer().array().indexWhere(_ == '\0')}")
//			payloadBuf.limit(payloadBuf.array().indexWhere(_ == 0))
//			println(s"buf len: ${payloadBuf.remaining()}")

			val parsed = StandardCharsets.UTF_8.decode(payloadBuf).toString
			val nullTerm = parsed.indexOf(0)
			val payload = parsed.substring(0, nullTerm)


			woofData.position(8)
			val unionBuf = woofData.slice()
			unionBuf.order(ByteOrder.LITTLE_ENDIAN)
			unionBuf.limit(8)

			val data = typ match {
				case 'd' | 'D' => unionBuf.getDouble()
				case 's' | 'S' => payload
				case 'i' | 'I' => unionBuf.getInt()
				case 'l' | 'L' =>	unionBuf.getLong()

			}

			//			println(payload.length())
			//			println(payload(43))
			//			println(payload(44))
			//			println(payload(45))
			//
			//			for (i <- 45 until 1028) {
			//				if (payload(i).toByte != 0) {
			//					println(s"idx: $i, val: ${payload(i).toByte}")
			//				}
			//			}
			//			println(payload.substring(40))

			println(s"type: $typ")
			println(s"ip: $resIp")
			println(s"tvSec: $tvSec")
			println(s"tvuSec: $tvuSec")
			println(s"data: $data")
			println(s"Done")
		}
		"split woofs by configured pattern" in {
			val mockConfig = mock[Configuration]
			when(mockConfig.get[FiniteDuration]("load_daemon.period")).thenReturn(10 minutes)

			val woofDAO = mock[MetricDAO]
			when(woofDAO.insertMetrics(any())) thenReturn Future.unit

			val source = Woof(
				"woof://127.0.0.1/dev/null",
				"Test Woof",
				Some(raw"""(.*?):(.*?)"""),
				Seq("Temperature", "Humidity")
			)

			val sourceDAO = mock[WoofDAO]
			when(sourceDAO.listWoofs) thenReturn Future.successful(Seq(source))

			val now = System.currentTimeMillis()
			val woofService = new WoofService(mock[ApplicationLifecycle], mockConfig, mock[MessageService], woofDAO, sourceDAO)
			val req = woofService.ingestMetrics(source, Seq(SensorPayload(
				SensorType.TEXT,
				Some("64.76:123.4"),
				None,
				now,
				1
			)))
			await(req)
			verify(woofDAO, atLeastOnce()).insertMetrics(Seq(Metric("woof://127.0.0.1/dev/null:Temperature", "woof://127.0.0.1/dev/null", now, 64.76, 1)))
			verify(woofDAO, atLeastOnce()).insertMetrics(Seq(Metric("woof://127.0.0.1/dev/null:Humidity", "woof://127.0.0.1/dev/null", now, 123.4, 1)))
		}
	}
}
