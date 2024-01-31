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

package io.renku.search.http.borer

import cats.effect.*
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.Decoder
import org.http4s.Request
import org.http4s.Method
import org.http4s.implicits.*
import org.http4s.headers.`Content-Type`
import io.renku.search.http.borer.EntityCodecTest.Room
import munit.CatsEffectSuite
import org.http4s.MediaType


class EntityCodecTest extends CatsEffectSuite:

  val room = Room(1, "meeting room", 55)

  test("test json"):
    import BorerEntityJsonCodec.given
    val req = Request[IO](Method.POST, uri"localhost").withEntity(room)
    assertEquals(req.headers.get[`Content-Type`], Some(`Content-Type`(MediaType.application.json)))
    assertEquals(req.bodyText.compile.string.unsafeRunSync(), s"""{"id":${room.id},"name":"${room.name}","seats":${room.seats}}""")

object EntityCodecTest:
  case class Room(id: Long, name: String, seats: Int)
  object Room:
    given Encoder[Room] = MapBasedCodecs.deriveEncoder
    given Decoder[Room] = MapBasedCodecs.deriveDecoder
