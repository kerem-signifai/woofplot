package model

import play.api.mvc.QueryStringBindable.Parsing

object Query {

  implicit object intervalBinder extends Parsing[Interval](
    Interval.fromString,
    _.toString,
    (key: String, _: Exception) => s"Cannot parse parameter $key as interval"
  )

  implicit object aggregationBinder extends Parsing[Aggregation](
    Aggregation.fromString,
    _.toString,
    (key: String, _: Exception) => s"Cannot parse parameter $key as aggregation"
  )

  object Interval {
    final val intervals = Seq(Moment, Minute, Hour, Day, Week, Month)
    def fromString(str: String): Interval = intervals.find(_.key equalsIgnoreCase str).get
  }

  sealed abstract class Interval(val key: String)

  case object Moment extends Interval("moment")
  case object Minute extends Interval("minute")
  case object Hour extends Interval("hour")
  case object Day extends Interval("day")
  case object Week extends Interval("week")
  case object Month extends Interval("month")

  object Aggregation {
    final val aggregations = Seq(Raw, Average, Count, Max, Min, Sum)
    def fromString(str: String): Aggregation = aggregations.find(_.key equalsIgnoreCase str).get
  }

  sealed abstract class Aggregation(val key: String)

  case object Raw extends Aggregation("raw")
  case object Average extends Aggregation("average")
  case object Count extends Aggregation("count")
  case object Max extends Aggregation("max")
  case object Min extends Aggregation("min")
  case object Sum extends Aggregation("sum")

  object Conversion {
    final val conversions = Seq(Identity, CelsiusToFahrenheit, FahrenheitToCelsius, KPHToMPH, MPHToKPH, MPStoMPH, MPHtoMPS)
    def find(str: String): Option[Conversion] = conversions.find(_.key equalsIgnoreCase str)
  }

  sealed abstract class Conversion(val key: String, val fx: Double => Double)

  case object Identity extends Conversion("identity", identity)
  case object CelsiusToFahrenheit extends Conversion("c2f", _ * (9D / 5) + 32)
  case object FahrenheitToCelsius extends Conversion("f2c", d => (d - 32) * (5D / 9))
  case object KPHToMPH extends Conversion("kph2mph", _ * 0.6213711922)
  case object MPHToKPH extends Conversion("mph2kph", _ / 0.6213711922)
  case object MPStoMPH extends Conversion("mps2mph", _ * 2.23694)
  case object MPHtoMPS extends Conversion("mph2mps", _ / 2.23694)

}
