package io.renku.search.events

import io.bullet.borer.*

opaque type MessageId = String

object MessageId:

  def apply(id: String): MessageId = id

  extension (self: MessageId)
    def value: String = self

    private def order = new Ordered[MessageId] {
      override def compare(that: MessageId): Int = self.compareTo(that)
    }
    export order.*

  given Decoder[MessageId] = Decoder.forString
  given Encoder[MessageId] = Encoder.forString
