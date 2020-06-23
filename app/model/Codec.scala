package model

import io.circe.{Decoder, Encoder}
import model.Query.Conversion

object Codec {

  implicit val sensorTypeEnc: Encoder[SensorType] = Encoder.encodeString.contramap(_.name())
  implicit val conversionEnc: Encoder[Conversion] = Encoder.encodeString.contramap(_.key)
  implicit val conversionDec: Decoder[Conversion] = Decoder.decodeString.emap { key =>
    Conversion.find(key) match {
      case Some(conversion) => Right(conversion)
      case _ => Left(s"Unable to find conversion $key")
    }
  }

}
