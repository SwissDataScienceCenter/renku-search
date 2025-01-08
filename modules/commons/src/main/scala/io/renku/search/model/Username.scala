package io.renku.search.model

import io.bullet.borer.Codec

opaque type Username = String
object Username:
  def apply(v: String): Username = v
  extension (self: Username) def value: String = self
  given Codec[Username] = Codec.bimap[String, Username](_.value, Username.apply)
