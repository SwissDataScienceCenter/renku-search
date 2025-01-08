package io.renku.search.model

import cats.{Eq, Order}

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

opaque type Keyword = String

object Keyword:
  def apply(kw: String): Keyword = kw

  given Eq[Keyword] = Eq.instance((a, b) => a.equalsIgnoreCase(b))
  given Order[Keyword] = Order.fromLessThan((a, b) => a.toLowerCase() < b.toLowerCase())
  given Encoder[Keyword] = Encoder.forString
  given Decoder[Keyword] = Decoder.forString

  extension (self: Keyword) def value: String = self
