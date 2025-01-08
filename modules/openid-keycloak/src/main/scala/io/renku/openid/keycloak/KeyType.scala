package io.renku.openid.keycloak

import io.bullet.borer.{Decoder, Encoder}

enum KeyType:
  case EC
  case RSA
  case OKP

  def name: String = productPrefix.toUpperCase

object KeyType:
  def fromString(s: String): Either[String, KeyType] =
    KeyType.values.find(_.name.equalsIgnoreCase(s)).toRight(s"Invalid key type: $s")

  given Decoder[KeyType] = Decoder.forString.mapEither(fromString)
  given Encoder[KeyType] = Encoder.forString.contramap(_.name)
