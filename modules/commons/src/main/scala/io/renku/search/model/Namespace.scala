package io.renku.search.model

import io.bullet.borer.{Decoder, Encoder}

opaque type Namespace = String

object Namespace:
  def apply(ns: String): Namespace = ns

  given Encoder[Namespace] = Encoder.forString
  given Decoder[Namespace] = Decoder.forString

  extension (self: Namespace) def value: String = self
