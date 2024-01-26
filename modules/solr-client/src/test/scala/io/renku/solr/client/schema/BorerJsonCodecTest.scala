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

import io.bullet.borer.Json
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
      """{"add-field":{"name":"description","type":"integer","required":false,"indexed":true,"stored":true,"multiValued":false,"uninvertible":true,"docValues":false}}"""
    )

  test("encode multiple schema commands into a single object"):
    val vs = Seq(
      DeleteType(TypeName("integer")),
      DeleteType(TypeName("float")),
      SchemaCommand.Add(Field(FieldName("description"), TypeName("text")))
    )
    assertEquals(
      Json.encode(vs).toUtf8String,
      """{"delete-field-type":{"name":"integer"},"delete-field-type":{"name":"float"},"add-field":{"name":"description","type":"text","required":false,"indexed":true,"stored":true,"multiValued":false,"uninvertible":true,"docValues":false}}""".stripMargin
    )
}
