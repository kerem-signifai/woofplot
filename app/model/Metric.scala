package model

case class Metric(
	source: String,
	woof: String,
	timestamp: Long,
	value: Double,
	seqNo: Long
)
