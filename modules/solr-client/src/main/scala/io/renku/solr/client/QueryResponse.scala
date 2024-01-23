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

import io.renku.avro.codec.AvroDecoder
import io.renku.avro.codec.all.given
import io.renku.avro.codec.json.AvroJsonDecoder
import io.renku.solr.client.messages.ResponseHeader
import org.apache.avro.{Schema, SchemaBuilder}

final case class QueryResponse[A](
    responseHeader: ResponseHeader,
    responseBody: ResponseBody[A]
)

object QueryResponse:
  // format: off
  private def makeSchema(docSchema: Schema) =
    SchemaBuilder.record("QueryResponse")
      .fields()
        .name("responseHeader").`type`(ResponseHeader.SCHEMA$).noDefault()
        .name("response").`type`(ResponseBody.bodySchema(docSchema)).noDefault()
      .endRecord()
  // format: on

  def makeDecoder[A](docSchema: Schema)(using
      AvroDecoder[A]
  ): AvroJsonDecoder[QueryResponse[A]] =
    AvroJsonDecoder.create[QueryResponse[A]](makeSchema(docSchema))
