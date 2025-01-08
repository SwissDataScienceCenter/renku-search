package io.renku.search.model

import io.bullet.borer.Codec

opaque type FirstName = String
object FirstName:
  def apply(v: String): FirstName = v
  extension (self: FirstName) def value: String = self
  given Codec[FirstName] = Codec.bimap[String, FirstName](_.value, FirstName.apply)
