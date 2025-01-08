package io.renku.openid.keycloak

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

enum KeyUse(val name: String):
  case Sign extends KeyUse("sig")
  case Encrypt extends KeyUse("enc")

object KeyUse:
  def fromString(s: String): Either[String, KeyUse] =
    KeyUse.values.find(_.name.equalsIgnoreCase(s)).toRight(s"Invalid key use: $s")

  given Decoder[KeyUse] = Decoder.forString.mapEither(fromString)
  given Encoder[KeyUse] = Encoder.forString.contramap(_.name)
