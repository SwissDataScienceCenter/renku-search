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

import cats.effect.IO
import io.renku.avro.codec.all.given
import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import io.renku.solr.client.SolrClientSpec.Room
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.{SolrSpec, SolrTruncate}
import munit.CatsEffectSuite
import org.apache.avro.{Schema, SchemaBuilder}

class SolrClientSpec extends CatsEffectSuite with SolrSpec with SolrTruncate:

  test("use schema for inserting and querying"):
    val cmds = Seq(
      SchemaCommand.Add(FieldType.text(TypeName("roomText"), Analyzer.classic)),
      SchemaCommand.Add(FieldType.int(TypeName("roomInt"))),
      SchemaCommand.Add(Field(FieldName("roomName"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomDescription"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomSeats"), TypeName("roomInt")))
    )
    withSolrClient().use { client =>
      for {
        _ <- truncateAll(client)(
          Seq(FieldName("name"), FieldName("description"), FieldName("seats")),
          Seq(TypeName("text"), TypeName("int"))
        )
        _ <- client.modifySchema(cmds)
        _ <- client
          .insert[Room](Room.schema, Seq(Room("meeting room", "room for meetings", 56)))
        r <- client.query[Room](Room.schema, QueryString("roomSeats > 10"))
        _ <- IO.println(r)
      } yield ()
    }

object SolrClientSpec:
  case class Room(name: String, description: String, seats: Int)
      derives AvroEncoder,
        AvroDecoder
  object Room:
    val schema: Schema =
      //format: off
      SchemaBuilder.record("Room").fields()
        .name("name").aliases("roomName").`type`("string").noDefault()
        .name("description").aliases("roomDescription").`type`("string").noDefault()
        .name("seats").aliases("roomSeats").`type`("int").withDefault(0)
        .endRecord()
      //format: on
