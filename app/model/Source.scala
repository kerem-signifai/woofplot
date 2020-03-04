package model

case class Source(
	id: String,
	name: String,
	pattern: String,
	datatypes: Seq[String],
	url: String
)
