package io.renku.search.model

import cats.Show

import io.bullet.borer.Codec

opaque type Id = String
object Id:
  def apply(v: String): Id = v
  extension (self: Id) def value: String = self
  given Codec[Id] = Codec.of[String]
  given Show[Id] = Show.show(_.value)
