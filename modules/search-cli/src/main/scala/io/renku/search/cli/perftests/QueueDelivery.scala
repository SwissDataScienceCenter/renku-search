package io.renku.search.cli.perftests

import cats.Show
import io.renku.avro.codec.AvroEncoder
import io.renku.queue.client.MessageHeader
import io.renku.redis.client.QueueName

private trait QueueDelivery:
  type P
  val queue: QueueName
  val header: MessageHeader
  val payload: P
  val encoder: AvroEncoder[P]

private object QueueDelivery:
  def apply[O: AvroEncoder](q: QueueName, h: MessageHeader, p: O): QueueDelivery =
    new QueueDelivery:
      override type P = O
      override val queue: QueueName = q
      override val header: MessageHeader = h
      override val payload: O = p
      override val encoder: AvroEncoder[O] = AvroEncoder[O]

  given Show[QueueDelivery] = Show.show { delivery =>
    s"""'${delivery.queue}' queue: '${delivery.payload}'"""
  }
