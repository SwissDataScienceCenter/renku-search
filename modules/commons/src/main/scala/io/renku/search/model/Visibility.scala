package io.renku.search.model

import cats.kernel.Order

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{Decoder, Encoder}

enum Visibility:
  lazy val name: String = productPrefix.toLowerCase
  case Public, Private

  override def toString(): String = name

object Visibility:
  given Order[Visibility] = Order.by(_.ordinal)
  given Encoder[Visibility] = Encoder.forString.contramap(_.name)
  given Decoder[Visibility] = Decoder.forString.mapEither(Visibility.fromString)

  def fromString(v: String): Either[String, Visibility] =
    Visibility.values
      .find(_.name.equalsIgnoreCase(v))
      .toRight(s"Invalid visibility: $v")

  def unsafeFromString(v: String): Visibility =
    fromString(v).fold(sys.error, identity)
