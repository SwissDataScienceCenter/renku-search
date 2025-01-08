package io.renku.search.model

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

enum EntityType:
  case Project
  case User
  case Group

  def name: String = productPrefix

object EntityType:
  def fromString(str: String): Either[String, EntityType] =
    EntityType.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid entity type: $str")

  def unsafeFromString(str: String): EntityType =
    fromString(str).fold(sys.error, identity)

  given Encoder[EntityType] = Encoder.forString.contramap(_.name)
  given Decoder[EntityType] = Decoder.forString.mapEither(fromString)
