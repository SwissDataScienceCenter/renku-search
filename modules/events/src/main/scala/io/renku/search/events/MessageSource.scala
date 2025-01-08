package io.renku.search.events

opaque type MessageSource = String
object MessageSource:
  def apply(v: String): MessageSource = v
  extension (self: MessageSource) def value: String = self
