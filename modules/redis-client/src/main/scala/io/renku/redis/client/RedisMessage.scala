/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.redis.client

import cats.syntax.all.*
import dev.profunktor.redis4cats.streams.data.XReadMessage
import io.renku.queue.client.{DataContentType, Header, Message, MessageId}
import scodec.bits.ByteVector

import java.time.Instant

private object RedisMessage:

  def bodyFrom(
      header: Header,
      payload: ByteVector
  ): Either[Throwable, Map[String, ByteVector]] =
    BodyMap()
      .add(MessageBodyKeys.payload, payload)
      .flatMap(_.add(MessageBodyKeys.contentType, header.dataContentType.mimeType))
      .flatMap(_.maybeAdd(MessageBodyKeys.source, header.source.map(_.value)))
      .flatMap(_.maybeAdd(MessageBodyKeys.messageType, header.messageType.map(_.value)))
      .flatMap(
        _.maybeAdd(MessageBodyKeys.schemaVersion, header.schemaVersion.map(_.value))
      )
      .flatMap(
        _.add(MessageBodyKeys.time, header.time.map(_.value).getOrElse(Instant.now()))
      )
      .flatMap(_.maybeAdd(MessageBodyKeys.requestId, header.requestId.map(_.value)))
      .map(_.body)

  def toMessage(
      rm: XReadMessage[String, ByteVector]
  ): Either[Throwable, Option[Message]] =
    val bodyMap = BodyMap(rm.body)
    for
      maybeContentType <- bodyMap
        .get[String](MessageBodyKeys.contentType)
        .flatMap(_.map(DataContentType.from).sequence)
      maybePayload <- bodyMap.get[ByteVector](MessageBodyKeys.payload)
    yield (maybeContentType, maybePayload)
      .mapN(Message(MessageId(rm.id.value), _, _))

  private case class BodyMap(body: Map[String, ByteVector] = Map.empty):

    def add[V](key: String, value: V)(using
        encoder: ValueEncoder[V]
    ): Either[Throwable, BodyMap] =
      encoder
        .encode(value)
        .map(encV => copy(body = body + (key -> encV)))

    def maybeAdd[V](key: String, maybeV: Option[V])(using
        encoder: ValueEncoder[V]
    ): Either[Throwable, BodyMap] =
      maybeV
        .map(add(key, _))
        .getOrElse(this.asRight)

    def apply[V](key: String)(using
        decoder: ValueDecoder[V]
    ): Either[Throwable, V] =
      get(key).flatMap(_.toRight(new Exception(s"No '$key' in Redis message")))

    def get[V](key: String)(using
        decoder: ValueDecoder[V]
    ): Either[Throwable, Option[V]] =
      body.get(key).map(decoder.decode).sequence

  private trait ValueEncoder[A]:
    def encode(v: A): Either[Throwable, ByteVector]
    def contramap[B](f: B => A): ValueEncoder[B] = (b: B) => encode(f(b))

  private object ValueEncoder:
    def apply[A](using enc: ValueEncoder[A]): ValueEncoder[A] = enc

  private given ValueEncoder[String] = ByteVector.encodeUtf8(_)
  private given ValueEncoder[ByteVector] = identity(_).asRight
  private given ValueEncoder[Long] = ByteVector.fromLong(_).asRight
  private given ValueEncoder[Instant] =
    ValueEncoder[Long].contramap[Instant](_.toEpochMilli)

  private trait ValueDecoder[A]:
    def decode(bv: ByteVector): Either[Throwable, A]
    def map[B](f: A => B): ValueDecoder[B] = (bv: ByteVector) => decode(bv).map(f)

  private object ValueDecoder:
    def apply[A](using dec: ValueDecoder[A]): ValueDecoder[A] = dec

  private given ValueDecoder[String] = _.decodeUtf8
  private given ValueDecoder[ByteVector] = identity(_).asRight
  private given ValueDecoder[Long] = _.toLong().asRight
  private given ValueDecoder[Instant] = ValueDecoder[Long].map(Instant.ofEpochMilli)
