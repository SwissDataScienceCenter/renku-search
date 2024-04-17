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

package io.renku.events

import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.{AvroDecoder, AvroEncoder, AvroIO}
import io.renku.events.v1.{ProjectCreated, Visibility}
import munit.FunSuite

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SerializeDeserializeSpec extends FunSuite {

  test("serialize and deserialize ProjectCreated") {
    val data = ProjectCreated(
      UUID.randomUUID().toString,
      "my-project",
      "slug",
      Seq.empty,
      Visibility.PUBLIC,
      Some("a description for it"),
      Seq("data", "science"),
      "created-by-me",
      Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )
    val avro = AvroIO(ProjectCreated.SCHEMA$)

    val bytes = avro.write(Seq(data))
    val decoded = avro.read[ProjectCreated](bytes)

    assertEquals(decoded, List(data))
  }

}
