package io.renku.search.query

import cats.kernel.Order

import io.bullet.borer.{Decoder, Encoder}

enum SortableField:
  case Name
  case Created
  case Score

  val name: String = Strings.lowerFirst(productPrefix)

object SortableField:
  given Encoder[SortableField] = Encoder.forString.contramap(_.name)
  given Decoder[SortableField] = Decoder.forString.mapEither(fromString)
  given Order[SortableField] = Order.by(_.name)

  private val allNames: String = SortableField.values.map(_.name).mkString(", ")

  def fromString(str: String): Either[String, SortableField] =
    SortableField.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid field: $str. Allowed are: $allNames")

  def unsafeFromString(str: String): SortableField =
    fromString(str).fold(sys.error, identity)
