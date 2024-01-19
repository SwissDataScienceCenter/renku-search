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

package io.renku.avro.codec.encoders

import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.decoders.all.given
import munit.FunSuite
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericFixed
import org.apache.avro.util.Utf8
import scodec.bits.ByteVector

class StringEncodersTest extends FunSuite {
  case class Foo(s: String) derives AvroDecoder, AvroEncoder

  test("encode strings") {
    val schema = SchemaBuilder
      .record("Foo")
      .fields()
      .name("s")
      .`type`("string")
      .noDefault()
      .endRecord()

    val record = AvroEncoder[Foo].encode(schema).apply(Foo("hello"))
    assertEquals(record, AvroRecord(schema, Seq(new Utf8("hello"))))
  }

  test("encode fixed strings") {
    val schema = SchemaBuilder.fixed("s").size(8)
    val res = AvroEncoder[String].encode(schema)("hello").asInstanceOf[GenericFixed]
    val data = ByteVector.view(res.bytes())
    assertEquals(data, ByteVector('h', 'e', 'l', 'l', 'o', 0, 0, 0))
  }
}
