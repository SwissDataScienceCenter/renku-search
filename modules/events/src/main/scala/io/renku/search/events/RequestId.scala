package io.renku.search.events

opaque type RequestId = String
object RequestId:
  def apply(v: String): RequestId = v
  extension (self: RequestId) def value: String = self
