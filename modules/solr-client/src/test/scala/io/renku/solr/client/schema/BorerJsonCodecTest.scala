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

package io.renku.solr.client.schema

import scala.io.Source

import io.bullet.borer.Json
import io.renku.solr.client.SchemaResponse
import io.renku.solr.client.schema.SchemaCommand.DeleteType
import munit.FunSuite

class BorerJsonCodecTest extends FunSuite with SchemaJsonCodec {

  test("encode schema command: delete type"):
    val v = DeleteType(TypeName("integer"))
    assertEquals(
      Json.encode(v).toUtf8String,
      """{"delete-field-type":{"name":"integer"}}"""
    )

  test("encode schema command: add"):
    val v = SchemaCommand.Add(Field(FieldName("description"), TypeName("integer")))
    assertEquals(
      Json.encode(v).toUtf8String,
      """{"add-field":{"name":"description","type":"integer"}}"""
    )

  test("encode multiple schema commands into a single object"):
    val vs = Seq(
      DeleteType(TypeName("integer")),
      DeleteType(TypeName("float")),
      SchemaCommand.Add(
        Field(FieldName("description"), TypeName("text"), required = true)
      )
    )
    assertEquals(
      Json.encode(vs).toUtf8String,
      """{"delete-field-type":{"name":"integer"},"delete-field-type":{"name":"float"},"add-field":{"name":"description","type":"text","required":true}}""".stripMargin
    )

  test("decode schema response"):
    val schemaResponseText = Source.fromResource("schema-response.json").mkString
    val result = Json.decode(schemaResponseText.getBytes()).to[SchemaResponse].value
    assertEquals(result.schema.copyFields.size, 16)
    assertEquals(result.schema.dynamicFields.size, 69)
    assertEquals(result.schema.fields.size, 30)
    assertEquals(result.schema.fieldTypes.size, 73)
    assert(result.schema.fields.exists(_.name == FieldName("_kind")))
    assert(result.schema.copyFields.exists(_.source == FieldName("description")))
}
