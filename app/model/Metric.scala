package model

case class Metric(
  woofId: Long,
  field: Int,
  timestamp: Long,
  value: Double
)
