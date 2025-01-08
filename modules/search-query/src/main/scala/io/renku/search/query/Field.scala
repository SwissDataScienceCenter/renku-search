package io.renku.search.query

import io.bullet.borer.{Decoder, Encoder}

enum Field:
  case Id
  case Name
  case Slug
  case Visibility
  case Created
  case CreatedBy
  case Type
  case Role
  case Keyword
  case Namespace

  val name: String = Strings.lowerFirst(productPrefix)

object Field:
  given Encoder[Field] = Encoder.forString.contramap(_.name)
  given Decoder[Field] = Decoder.forString.mapEither(fromString)

  private val allNames: String = Field.values.map(_.name).mkString(", ")

  def fromString(str: String): Either[String, Field] =
    Field.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid field: $str. Allowed are: $allNames")

  def unsafeFromString(str: String): Field =
    fromString(str).fold(sys.error, identity)
