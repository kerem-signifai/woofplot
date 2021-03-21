package model

case class SensorPayload(
  typ: SensorType,
  text: Option[String],
  number: Option[Double],
  timestamp: Long
)
