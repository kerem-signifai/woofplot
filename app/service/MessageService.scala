package service

import java.nio.charset.StandardCharsets
import java.nio.{ByteBuffer, ByteOrder}

import javax.inject.{Inject, Singleton}
import model.{SensorPayload, SensorType}
import org.apache.logging.log4j.scala.Logging
import org.zeromq.ZThread.IAttachedRunnable
import org.zeromq._
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{Future, Promise}
import scala.util.Success

@Singleton
class MessageService @Inject()(
	applicationLifecycle: ApplicationLifecycle,
	config: Configuration
) extends Logging {
	private val threadCount = config.get[Int]("messenger.thread_count")

	sealed trait MessageType[T] {
		val woof: String
		val p: Promise[T]
	}
	case class GetElementSize(woof: String, p: Promise[Int]) extends MessageType[Int]
	case class GetLatestSeqNo(woof: String, p: Promise[Int]) extends MessageType[Int]
	case class Fetch(woof: String, seqNo: Int, p: Promise[SensorPayload]) extends MessageType[SensorPayload]

	private val requests: java.util.concurrent.ConcurrentLinkedQueue[MessageType[_ <: Any]] = new java.util.concurrent.ConcurrentLinkedQueue()

	val parentCtx = new ZContext()
	for (_ <- 1 to threadCount) {
		ZThread.fork(parentCtx, new MessageProcessor())
	}
	applicationLifecycle addStopHook (() => Future.successful(parentCtx.close()))

	class MessageProcessor extends IAttachedRunnable {
		private final val WOOF_MSG_GET_EL_SIZE = 2
		private final val WOOF_MSG_GET = 3
		private final val WOOF_MSG_GET_LATEST_SEQNO = 5

		private final val REQ_TIMEOUT = 120000L

		private final val WOOF_PARSE_PATTERN = raw"""^[^:\/?#]+:?//(.*?)(/.+?)(/.+?)""".r

		def dispatchOne(msg: ZMsg)(implicit sock: ZMQ.Socket): ByteBuffer = {
			if (msg.send(sock)) {
				val msg = ZMsg.recvMsg(sock)
				if (msg == null) throw new RuntimeException("Failed to receive response")
				sock.close()
				ByteBuffer.wrap(msg.getFirst.getData)
			} else {
				throw new RuntimeException("Failed to send message")
			}
		}

		def parseWoof(woof: String): Option[(String, Long)] = {
			woof match {
				case WOOF_PARSE_PATTERN(host, namespace, _) =>
					val port = (namespace.foldLeft(BigInt(5381L)) { (i, c) => (c + (i * 33L)) % (2 * BigInt(Long.MaxValue + 1)) } % 10000L) + 50000L
					Some(host -> port.longValue())
				case _ => None
					throw new IllegalArgumentException(s"Invalid URL for woof: $woof")
			}
		}

		override def run(args: Array[AnyRef], ctx: ZContext, pipe: ZMQ.Socket): Unit = {
			while (!Thread.currentThread().isInterrupted) {
				pipe.recv(ZMQ.DONTWAIT)
				val request = requests.poll()
				if (request != null) {
					logger.info(s"Processing message $request")

					parseWoof(request.woof) match {
						case Some((host, port)) =>
							val now = System.currentTimeMillis()
							implicit val sock: ZMQ.Socket = ctx.createSocket(SocketType.REQ)
							val connected = sock.connect(s"tcp://$host:$port")
							if (!connected) throw new RuntimeException("Failed to connect to woof")

							try {
								request match {

									case GetElementSize(woof, p) =>
										val elementSizeMsg = new ZMsg()
										elementSizeMsg.addString(WOOF_MSG_GET_EL_SIZE.toString)
										elementSizeMsg.addString(woof)

										val elementSize = StandardCharsets.UTF_8.decode(dispatchOne(elementSizeMsg)).toString.toInt
										p.complete(Success(elementSize))

									case GetLatestSeqNo(woof, p) =>
										val latestSeqNoMsg = new ZMsg()
										latestSeqNoMsg.addString(WOOF_MSG_GET_LATEST_SEQNO.toString)
										latestSeqNoMsg.addString(woof)

										val latestSeqNo = StandardCharsets.UTF_8.decode(dispatchOne(latestSeqNoMsg)).toString.toInt
										val elapsed = System.currentTimeMillis() - now
										println(s"Elapsed: ${elapsed}ms")
										p.complete(Success(latestSeqNo))

									case Fetch(woof, seqNo, p) =>
										val woofGetMsg = new ZMsg()
										woofGetMsg.addString(WOOF_MSG_GET.toString)
										woofGetMsg.addString(woof)
										woofGetMsg.addString(seqNo.toString)

										val woofData = dispatchOne(woofGetMsg)
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
										val parsed = StandardCharsets.UTF_8.decode(payloadBuf).toString
										val payload = parsed.substring(0, parsed.indexOf(0))

										woofData.position(8)
										val unionBuf = woofData.slice()
										unionBuf.order(ByteOrder.LITTLE_ENDIAN)
										unionBuf.limit(8)

										val (textData, numData, sensorType) = typ match {
											case 'd' | 'D' => (None, Some(unionBuf.getDouble()), SensorType.NUMERIC)
											case 's' | 'S' => (Some(payload), None, SensorType.TEXT)
											case 'i' | 'I' => (None, Some(unionBuf.getInt().toDouble), SensorType.NUMERIC)
											case 'l' | 'L' => (None, Some(unionBuf.getLong().toDouble), SensorType.NUMERIC)
										}

										val data = SensorPayload(sensorType, textData, numData, 1000L * (tvSec + tvuSec / 1000000), seqNo)
										p.complete(Success(data))

									case _ => request.p.failure(new IllegalArgumentException(s"Failed to identify message: $request"))
								}

							} catch {
								case e: Throwable => request.p.failure(new RuntimeException(s"Failed to process message: $request", e))
							} finally {
								sock.close()
						}
						case _ => request.p.failure(new IllegalArgumentException(s"Failed to parse woof: ${request.woof}"))
					}
				}
			}
			ctx.close()
		}
	}

	def getElementSize(woof: String): Future[Int] = {
		val p = Promise[Int]()
		requests.add(GetElementSize(woof, p))
		p.future
	}

	def getLatestSeqNo(woof: String): Future[Int] = {
		val p = Promise[Int]()
		requests.add(GetLatestSeqNo(woof, p))
		p.future
	}

	def fetch(woof: String, seqNo: Int): Future[SensorPayload] = {
		val p = Promise[SensorPayload]()
		requests.add(Fetch(woof, seqNo, p))
		p.future
	}

}
