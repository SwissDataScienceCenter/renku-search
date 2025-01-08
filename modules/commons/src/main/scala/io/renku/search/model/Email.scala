package io.renku.search.model

import io.bullet.borer.Codec

opaque type Email = String
object Email:
  def apply(v: String): Email = v
  extension (self: Email) def value: String = self
  given Codec[Email] = Codec.bimap[String, Email](_.value, Email.apply)
