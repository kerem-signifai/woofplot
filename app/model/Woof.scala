package model

import model.Query.Conversion

case class WoofBlueprint(
  url: String,
  name: String,
  columns: Seq[Column]
)

case class Woof(
  woofId: Long,
  url: String,
  name: String,
  columns: Seq[Column],
  latestSeqNo: Long
)

case class Column(
  field: Int,
  name: String,
  conversion: Conversion
)
