package model

import model.Query.Conversion

case class Woof(
	url: String,
	name: String,
	pattern: Option[String],
	fields: Seq[WoofField]
)

case class WoofField(
	label: String,
	conversion: Conversion
)
