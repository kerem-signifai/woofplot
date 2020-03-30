package model

case class Woof(
	url: String,
	name: String,
	pattern: Option[String],
	dataLabels: Seq[String]
)
