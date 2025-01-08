package io.renku.search.events

trait EventMessageDecoder[T]:
  def decode(qm: QueueMessage): Either[DecodeFailure, EventMessage[T]]

object EventMessageDecoder:
  def apply[T](using emd: EventMessageDecoder[T]): EventMessageDecoder[T] = emd
  def instance[T](
      f: QueueMessage => Either[DecodeFailure, EventMessage[T]]
  ): EventMessageDecoder[T] =
    (qm: QueueMessage) => f(qm)
