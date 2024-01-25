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

package io.renku.solr.client

import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.{Decoder, Encoder, Json, Writer}
import io.renku.solr.client.JsonEncodingTest.Room
import munit.FunSuite

class JsonEncodingTest extends FunSuite {

  test("test with discriminator"):
    val r = Room("meeting room", 59)
    val json = Json.encode(r).toUtf8String
    val rr = Json.decode(json.getBytes).to[Room].value
    assertEquals(json, """{"_type":"Room","name":"meeting room","seats":59}""")
    assertEquals(rr, r)
}

object JsonEncodingTest:
  case class Room(name: String, seats: Int)
  object Room:
    given Decoder[Room] = deriveDecoder
    given Encoder[Room] = EncoderSupport.deriveWithDiscriminator[Room]
