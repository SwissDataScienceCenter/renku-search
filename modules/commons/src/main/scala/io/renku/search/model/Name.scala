package io.renku.search.model

import io.bullet.borer.Codec

opaque type Name = String
object Name:
  def apply(v: String): Name = v
  extension (self: Name) def value: String = self
  given Codec[Name] = Codec.of[String]
