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

package io.renku.search.events

import munit.*
import java.time.Instant
import io.renku.events.v1.Header as HeaderV1
import io.renku.events.Header as HeaderV2
import io.renku.avro.codec.AvroWriter
import io.renku.avro.codec.all.given
import io.renku.search.model.Timestamp

class MessageHeaderSpec extends FunSuite:

  test("read v1 header"):
    val now = Instant.now
    val hv1 = HeaderV1(
      "the-source",
      "type2",
      DataContentType.Binary.mimeType,
      "v1",
      now,
      "req1"
    )
    val bv1 = AvroWriter(HeaderV1.SCHEMA$).write(Seq(hv1))
    val h = MessageHeader.fromByteVector(bv1).fold(sys.error, identity)
    assertEquals(
      h,
      MessageHeader(
        MessageSource("the-source"),
        DataContentType.Binary,
        SchemaVersion.V1,
        Timestamp(now),
        RequestId("req1")
      )
    )

  test("read v2 header"):
    val now = Instant.now
    val hv2 = HeaderV2(
      "the-source",
      "type2",
      DataContentType.Binary.mimeType,
      "v1",
      now,
      "req1"
    )
    val bv1 = AvroWriter(HeaderV2.SCHEMA$).write(Seq(hv2))
    val h = MessageHeader.fromByteVector(bv1).fold(sys.error, identity)
    assertEquals(
      h,
      MessageHeader(
        MessageSource("the-source"),
        DataContentType.Binary,
        SchemaVersion.V1,
        Timestamp(now),
        RequestId("req1")
      )
    )
