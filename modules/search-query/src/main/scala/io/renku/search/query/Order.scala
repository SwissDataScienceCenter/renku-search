package io.renku.search.query

import cats.data.NonEmptyList
import cats.syntax.all.*

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.query.parse.QueryParser

final case class Order(fields: NonEmptyList[Order.OrderedBy]):
  def render: String =
    s"sort:${fields.render}"

object Order:
  def apply(field: OrderedBy, fields: OrderedBy*): Order =
    Order(NonEmptyList(field, fields.toList))

  def apply(
      field: (SortableField, Direction),
      fields: (SortableField, Direction)*
  ): Order =
    Order(NonEmptyList(field, fields.toList).map(OrderedBy.apply.tupled))

  enum Direction:
    case Asc
    case Desc

    def name: String = productPrefix.toLowerCase

  object Direction:
    def fromString(s: String): Either[String, Direction] =
      Direction.values
        .find(_.name.equalsIgnoreCase(s))
        .toRight(s"Invalid sort direction: $s")

    def unsafeFromString(s: String): Direction =
      fromString(s).fold(sys.error, identity)

  final case class OrderedBy(
      field: SortableField,
      direction: Order.Direction
  ):
    def render: String = s"${field.name}-${direction.name}"

  object OrderedBy:
    given Encoder[OrderedBy] = Encoder.forString.contramap(_.render)
    given Decoder[OrderedBy] =
      Decoder.forString.mapEither(s => QueryParser.orderedBy.parseAll(s).leftMap(_.show))
    given cats.Order[OrderedBy] = cats.Order.by(_.render)

  def fromString(s: String): Either[String, Order] =
    QueryParser.sortTerm.parseAll(s).leftMap(_.show)

  def unsafeFromString(s: String): Order =
    fromString(s).fold(sys.error, identity)

  extension (self: NonEmptyList[OrderedBy])
    def render: String = self.map(_.render).toList.mkString(",")
