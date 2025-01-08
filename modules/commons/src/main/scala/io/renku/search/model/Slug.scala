package io.renku.search.model

import cats.kernel.Order

import io.bullet.borer.Codec

opaque type Slug = String
object Slug:
  def apply(v: String): Slug = v
  extension (self: Slug) def value: String = self
  given Codec[Slug] = Codec.of[String]
  given Order[Slug] = Order.fromComparable[String]
