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
import io.renku.solr.client.SolrClientSpec.{Person, Room}
import io.renku.solr.client.schema.{
  Analyzer,
  Field,
  FieldName,
  FieldType,
  SchemaCommand,
  TypeName
}
import io.renku.solr.client.util.{SolrSpec, SolrTruncate}
import munit.CatsEffectSuite
import org.apache.avro.{Schema, SchemaBuilder}

class SolrClientSpec extends CatsEffectSuite with SolrSpec with SolrTruncate:

  test("query something"):
    withSolrClient().use { client =>
      client.query[Person](Person.schema, QueryString("*:*"))
    }

  test("modify schema"):
    val cmds = Seq(
      SchemaCommand.Add(FieldType.text(TypeName("text"), Analyzer.classic)),
      SchemaCommand.Add(FieldType.int(TypeName("int"))),
      SchemaCommand.Add(Field(FieldName("name"), TypeName("text"))),
      SchemaCommand.Add(Field(FieldName("description"), TypeName("text"))),
      SchemaCommand.Add(Field(FieldName("seats"), TypeName("int")))
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
        r <- client.query[Room](Room.schema, QueryString("seats > 10"))
        _ <- IO.println(r)
      } yield ()
    }

  test("insert something"):
    withSolrClient().use { client =>
      val data = Person("Hugo", 34)
      for {
        _ <- truncateAll(client)(
          Seq(FieldName("name"), FieldName("description"), FieldName("seats")),
          Seq(TypeName("text"), TypeName("int"))
        )
        _ <- client.insert(Person.schema, Seq(data))
        r <- client.query[Person](Person.schema, QueryString("*:*"))
        _ = assert(r.responseBody.docs contains data)
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
        .name("name").`type`("string").noDefault()
        .name("description").`type`("string").noDefault()
        .name("seats").`type`("int").withDefault(0)
        .endRecord()
      //format: on

  // the List[…] is temporary until a proper solr schema is defined. by default it uses arrays
  case class Person(name: List[String], age: List[Int]) derives AvroDecoder, AvroEncoder
  object Person:
    def apply(name: String, age: Int): Person = Person(List(name), List(age))

    // format: off
    val schema: Schema = SchemaBuilder
      .record("Person")
        .fields()
          .name("name").`type`(SchemaBuilder.array().items().`type`("string")).noDefault()
          .name("age").`type`(SchemaBuilder.array().items().`type`("int")).noDefault()
      .endRecord()
    // format: on
