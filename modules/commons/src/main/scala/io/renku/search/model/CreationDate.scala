package io.renku.search.model

import java.time.Instant

import cats.kernel.Order

import io.bullet.borer.Codec
import io.renku.json.codecs.all.given

opaque type CreationDate = Instant
object CreationDate:
  def apply(v: Instant): CreationDate = v
  extension (self: CreationDate) def value: Instant = self
  given Codec[CreationDate] = Codec.of[Instant]
  given Order[CreationDate] = Order.fromComparable[Instant]
