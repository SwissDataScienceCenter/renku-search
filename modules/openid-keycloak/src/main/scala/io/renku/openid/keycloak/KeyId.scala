package io.renku.openid.keycloak

import io.bullet.borer.{Decoder, Encoder}

opaque type KeyId = String

object KeyId:
  def apply(id: String): KeyId = id

  given Decoder[KeyId] = Decoder.forString
  given Encoder[KeyId] = Encoder.forString

  extension (self: KeyId) def value: String = self
