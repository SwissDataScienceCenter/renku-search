package io.renku.search.cli.perftests

import cats.Show

import io.renku.avro.codec.AvroEncoder
import io.renku.redis.client.QueueName
import io.renku.search.events.*

private trait QueueDelivery:
  type P <: RenkuEventPayload
  def queue: QueueName
  def encoder: AvroEncoder[P]
  def message: EventMessage[P]

private object QueueDelivery:
  def apply[A <: RenkuEventPayload: AvroEncoder](
      q: QueueName,
      msg: EventMessage[A]
  ): QueueDelivery =
    new QueueDelivery:
      override type P = A
      override val encoder: AvroEncoder[P] = summon[AvroEncoder[P]]
      override val queue: QueueName = q
      override val message: EventMessage[P] = msg

  given Show[QueueDelivery] = Show.show { delivery =>
    s"""queue: '${delivery.queue}' msg: ${delivery.message.toString}"""
  }
