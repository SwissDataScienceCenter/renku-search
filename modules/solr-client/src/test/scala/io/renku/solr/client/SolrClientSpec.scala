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
import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import io.renku.avro.codec.all.given
import io.renku.solr.client.SolrClientSpec.Person
import io.renku.solr.client.util.SolrSpec
import munit.CatsEffectSuite
import org.apache.avro.{Schema, SchemaBuilder}

class SolrClientSpec extends CatsEffectSuite with SolrSpec:

  test("query something"):
    withSolrClient().use { client =>
      client.query[Person](Person.schema, QueryString("*:*"))
    }

  test("insert something"):
    withSolrClient().use { client =>
      val data = Person("Hugo", 34)
      for {
        _ <- client.insert(Person.schema, Seq(data))
        r <- client.query[Person](Person.schema, QueryString("*:*"))
        _ = assert(r.responseBody.docs contains data)
      } yield ()
    }

object SolrClientSpec:
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
