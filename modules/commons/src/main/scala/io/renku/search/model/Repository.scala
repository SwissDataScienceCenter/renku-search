package io.renku.search.model

import cats.kernel.Order

import io.bullet.borer.Codec

opaque type Repository = String
object Repository:
  def apply(v: String): Repository = v
  extension (self: Repository) def value: String = self
  given Codec[Repository] = Codec.of[String]
  given Order[Repository] = Order.fromComparable[String]
