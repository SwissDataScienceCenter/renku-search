package io.renku.search.query

import cats.data.NonEmptyList
import cats.kernel.Monoid
import cats.syntax.all.*

import io.renku.search.model.*
import io.renku.search.query.FieldTerm.Created
import io.renku.search.query.Query.Segment
import io.renku.search.query.parse.{QueryParser, QueryUtil}

final case class Query(
    segments: List[Query.Segment]
):
  def render: String =
    segments
      .map {
        case Query.Segment.Field(v) => v.asString
        case Query.Segment.Text(v)  => v
        case Query.Segment.Sort(v)  => v.render
      }
      .mkString(" ")

  def isEmpty: Boolean = segments.isEmpty

object Query:
  def parse(str: String): Either[String, Query] =
    val trimmed = str.trim
    if (trimmed.isEmpty) Right(empty)
    else
      QueryParser.query
        .parseAll(trimmed)
        .leftMap(_.show)
        .map(QueryUtil.collapse)

  enum Segment:
    case Field(value: FieldTerm)
    case Text(value: String)
    case Sort(value: Order)

  object Segment:
    given Monoid[Segment.Text] =
      Monoid.instance(Text(""), _ ++ _)

    extension (self: Segment.Text)
      def ++(other: Segment.Text): Segment.Text =
        if (other.value.isEmpty) self
        else if (self.value.isEmpty) other
        else Segment.Text(s"${self.value} ${other.value}")

    def sort(order: Order.OrderedBy, more: Order.OrderedBy*): Segment =
      Segment.Sort(Order(NonEmptyList(order, more.toList)))

    def text(phrase: String): Segment =
      Segment.Text(phrase)

    def typeIs(value: EntityType, more: EntityType*): Segment =
      Segment.Field(FieldTerm.TypeIs(NonEmptyList(value, more.toList)))

    def idIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.IdIs(NonEmptyList(value, more.toList)))

    def nameIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.NameIs(NonEmptyList(value, more.toList)))

    def slugIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.SlugIs(NonEmptyList(value, more.toList)))

    def visibilityIs(value: Visibility, more: Visibility*): Segment =
      Segment.Field(FieldTerm.VisibilityIs(NonEmptyList(value, more.toList)))

    def creationDateIs(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.Is, NonEmptyList(date, dates.toList)))

    def creationDateLt(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.LowerThan, NonEmptyList(date, dates.toList)))

    def creationDateGt(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.GreaterThan, NonEmptyList(date, dates.toList)))

    def creationDateIs(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateIs(DateTimeRef(date), dates.map(DateTimeRef.apply)*)

    def creationDateGt(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateGt(DateTimeRef(date), dates.map(DateTimeRef.apply)*)

    def creationDateLt(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateLt(DateTimeRef(date), dates.map(DateTimeRef.apply)*)

  val empty: Query = Query(Nil)

  def apply(s: Segment*): Query = Query(s.toList)
