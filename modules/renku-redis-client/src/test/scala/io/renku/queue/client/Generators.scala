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

package io.renku.queue.client

import java.time.Instant

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.AvroWriter
import io.renku.redis.client.RedisClientGenerators
import org.apache.avro.Schema
import org.scalacheck.Gen
import scodec.bits.ByteVector

object Generators:

  val requestIdGen: Gen[RequestId] = Gen.uuid.map(_.toString).map(RequestId(_))

  val creationTimeGen: Gen[CreationTime] =
    Gen
      .choose(
        Instant.parse("2020-01-01T01:00:00Z").toEpochMilli(),
        Instant.now().toEpochMilli()
      )
      .map(millis => CreationTime(Instant.ofEpochMilli(millis)))

  val schemaVersionGen: Gen[SchemaVersion] =
    Gen.oneOf(SchemaVersion.V1, SchemaVersion.V2)

  def messageHeaderGen(schema: Schema, contentType: DataContentType): Gen[MessageHeader] =
    messageHeaderGen(schema, Gen.const(contentType))

  def messageHeaderGen(
      schema: Schema,
      ctGen: Gen[DataContentType] = Gen.oneOf(DataContentType.values.toList)
  ): Gen[MessageHeader] =
    for
      contentType <- ctGen
      schemaVersion <- schemaVersionGen
      requestId <- requestIdGen
      creationTime <- creationTimeGen
    yield MessageHeader(
      MessageSource("test"),
      schema,
      contentType,
      schemaVersion,
      creationTime,
      requestId
    )

  def queueMessageGen[A: AvroEncoder](schema: Schema, payload: A): Gen[QueueMessage] =
    for
      id <- RedisClientGenerators.messageIdGen
      header <- messageHeaderGen(schema)
      encodedPayload = header.dataContentType match {
        case DataContentType.Binary => AvroWriter(schema).write(Seq(payload))
        case DataContentType.Json   => AvroWriter(schema).writeJson(Seq(payload))
      }
      h = header.toSchemaHeader(payload)
    yield QueueMessage(id, h, encodedPayload)
