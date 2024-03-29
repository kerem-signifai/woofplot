package service

import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.nio.{ByteBuffer, ByteOrder}

import akka.actor.{ActorSystem, Scheduler}
import javax.inject.{Inject, Singleton}
import model.{SensorPayload, SensorType}
import org.apache.logging.log4j.scala.Logging
import org.zeromq._
import play.api.Configuration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageService @Inject()(
  config: Configuration,
  actorSystem: ActorSystem
)(implicit ec: ExecutionContext) extends Logging {
  private final val WOOF_MSG_GET_EL_SIZE = 2
  private final val WOOF_MSG_GET_TAIL = 4
  private final val WOOF_MSG_GET_LATEST_SEQNO = 5

  private final val WOOF_PARSE_PATTERN = raw"""^woof:?//(.*?)(/.+)(/.+)$$""".r

  private final val MaxTime = 1516047601.0 + (100.0 * 365.0 * 86400.0)
  private final val MinTime = 1516047601.0 - (365.0 * 86400.0)
  private final val UnsignedLongMask = (BigInt(1) << java.lang.Long.SIZE) - 1

  private implicit val scheduler: Scheduler = actorSystem.scheduler
  private val ctx: ZMQ.Context = ZMQ.context(1)

  private val zmqTimeout = config.get[FiniteDuration]("zmq.timeout").toMillis

  private def parseWoof(woof: String): Option[(String, Long)] = {
    woof match {
      case WOOF_PARSE_PATTERN(host, namespace, _) =>
        val port = (namespace.foldLeft(BigInt(5381L)) { (i, c) => (c + (i * 33L)) % (2 * BigInt(Long.MaxValue + 1)) } % 10000L) + 50000L
        Some(host -> port.toLong)
      case _ => None
    }
  }

  private def dispatch(woof: String, msg: ZMsg): ZMsg = {
    parseWoof(woof) match {
      case Some((host, port)) =>
        val sock: ZMQ.Socket = ctx.socket(SocketType.REQ)
        val poller: ZMQ.Poller = ctx.poller(1)

        val connected = sock.connect(s"tcp://$host:$port")
        try {
          if (!connected) throw new RuntimeException("Failed to connect to woof")
          if (msg.send(sock)) {
            poller.register(sock)
            val resp = poller.poll(zmqTimeout)
            if (resp > 0) {
              val msg = ZMsg.recvMsg(sock, ZMQ.DONTWAIT)
              if (msg == null) throw new RuntimeException("Failed to receive response")
              msg
            } else {
              throw new SocketException("Polling failed")
            }
          } else {
            throw new RuntimeException("Failed to send message")
          }
        } finally {
          poller.close()
          sock.close()
        }
      case _ => throw new IllegalArgumentException(s"Failed to parse woof $woof")
    }
  }

  private def asUTF8(frame: ZFrame): String = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(frame.getData)).toString

  // See https://github.com/MAYHEM-Lab/cspot/blob/master/apps/senspot/senspot.c#L12
  private def readTimestamp(tvSec: Int, tvuSec: Int, padding: Int): Long = {
    val sbi = tvSec & UnsignedLongMask
    val usbi = tvuSec & UnsignedLongMask
    val timestamp = BigDecimal(sbi) + (BigDecimal(usbi) / 1000000.0)

    if (timestamp <= MinTime || timestamp >= MaxTime) {
      (1000 * (Integer.reverseBytes(tvuSec) + (Integer.reverseBytes(padding) / 1000000.0))).asInstanceOf[Long]
    } else {
      1000 * timestamp.toLong
    }
  }

  def getElementSize(woof: String): Future[Int] = {
    logger.info(s"Fetching element size of woof $woof")
    val elementSizeMsg = new ZMsg()
    elementSizeMsg.addString(WOOF_MSG_GET_EL_SIZE.toString)
    elementSizeMsg.addString(woof)

    retry {
      dispatch(woof, elementSizeMsg)
    } map { msg =>
      asUTF8(msg.getFirst).toInt
    }
  }

  def getLatestSeqNo(woofUrl: String): Future[Long] = {
    logger.info(s"Fetching latest sequence number from woof at $woofUrl")
    val latestSeqNoMsg = new ZMsg()
    latestSeqNoMsg.addString(WOOF_MSG_GET_LATEST_SEQNO.toString)
    latestSeqNoMsg.addString(woofUrl)

    retry {
      dispatch(woofUrl, latestSeqNoMsg)
    } map { msg =>
      asUTF8(msg.getFirst).toLong
    }
  }

  def fetch(woofUrl: String, elements: Int): Future[Seq[SensorPayload]] = {
    logger.info(s"Fetching most recent $elements elements from woof at $woofUrl")
    val woofGetMsg = new ZMsg()
    woofGetMsg.addString(WOOF_MSG_GET_TAIL.toString)
    woofGetMsg.addString(woofUrl)
    woofGetMsg.add(elements.toString)

    getElementSize(woofUrl).flatMap { elementSize =>
      retry {
        dispatch(woofUrl, woofGetMsg)
      } map { msg =>
        val sizeFrame = msg.pop()
        val tailFrame = msg.pop()

        val recvElCount = asUTF8(sizeFrame).toInt
        logger.info(s"Received $recvElCount elements of woof $woofUrl")

        tailFrame.getData.grouped(elementSize).map { bb =>
          val woofData = ByteBuffer.wrap(bb)
          val typ = woofData.get().toChar

          woofData.position(44)
          val tvSec = woofData.getInt()
          val tvuSec = woofData.getInt()
          woofData.getInt()
          val padding = woofData.getInt()

          woofData.position(60)
          val payloadBuf = woofData.slice()
          val parsed = StandardCharsets.UTF_8.decode(payloadBuf).toString
          val payload = parsed.substring(0, parsed.indexOf(0))

          woofData.position(8)
          val unionBuf = woofData.slice()
          unionBuf.order(ByteOrder.LITTLE_ENDIAN)
          unionBuf.limit(8)

          val (textData, numData, sensorType) = typ match {
            case 's' | 'S' => (Some(payload), None, SensorType.TEXT)
            case 'd' | 'D' => (None, Some(unionBuf.getDouble()), SensorType.NUMERIC)
            case 'i' | 'I' => (None, Some(unionBuf.getInt().toDouble), SensorType.NUMERIC)
            case 'l' | 'L' => (None, Some(unionBuf.getLong().toDouble), SensorType.NUMERIC)
          }

          SensorPayload(sensorType, textData, numData, readTimestamp(tvSec, tvuSec, padding))
        }.toSeq
      }
    }
  }
}
