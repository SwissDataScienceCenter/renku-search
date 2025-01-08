package io.renku.redis.client

opaque type QueueName = String
object QueueName:
  def apply(v: String): QueueName = v
  extension (self: QueueName) def name: String = self

opaque type ClientId = String
object ClientId:
  def apply(v: String): ClientId = v
  extension (self: ClientId) def value: String = self

type MessageId = String
