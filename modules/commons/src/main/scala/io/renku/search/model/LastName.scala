package io.renku.search.model

import io.bullet.borer.Codec

opaque type LastName = String
object LastName:
  def apply(v: String): LastName = v
  extension (self: LastName) def value: String = self
  given Codec[LastName] = Codec.bimap[String, LastName](_.value, LastName.apply)
