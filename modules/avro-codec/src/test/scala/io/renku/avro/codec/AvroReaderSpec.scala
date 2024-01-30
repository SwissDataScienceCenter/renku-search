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

package io.renku.avro.codec

import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import munit.FunSuite
import org.apache.avro.SchemaBuilder

class AvroReaderSpec extends FunSuite {

  case class Foo(name: String, age: Int) derives AvroDecoder, AvroEncoder
  val fooSchema = SchemaBuilder
    .record("Foo")
    .fields()
    .name("name")
    .`type`("string")
    .noDefault()
    .name("age")
    .`type`("int")
    .noDefault()
    .endRecord()

  val avro = AvroIO(fooSchema)

  test("read/write binary single") {
    val data = Foo("eddi", 55)
    val wire = avro.write(Seq(data))
    val result = avro.read[Foo](wire)
    assertEquals(result, List(data))
  }

  test("read/write binary multiple values") {
    val values = (1 to 10).toList.map(n => Foo("eddi", 50 + n))
    val wire = avro.write(values)
    val result = avro.read[Foo](wire)
    assertEquals(result, values)
  }

  test("read/write json values") {
    val values = (1 to 10).toList.map(n => Foo("eddi", 50 + n))
    val wire = avro.writeJson(values)
    val result = avro.readJson[Foo](wire)
    assertEquals(result, values)
  }

  test("read/write container") {
    val values = (1 to 10).toList.map(n => Foo("eddi", 50 + n))
    val wire = avro.writeContainer(values)
    val result = avro.readContainer[Foo](wire)
    assertEquals(result, values)
  }
}
