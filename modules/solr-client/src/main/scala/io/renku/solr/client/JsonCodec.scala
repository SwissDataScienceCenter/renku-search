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

import io.renku.avro.codec.json.{AvroJsonDecoder, AvroJsonEncoder}
import io.renku.avro.codec.all.given
import io.renku.solr.client.messages.QueryData

private[client] trait JsonCodec {

  given AvroJsonDecoder[QueryData] = AvroJsonDecoder.create(QueryData.SCHEMA$)
  given AvroJsonEncoder[QueryData] = AvroJsonEncoder.create(QueryData.SCHEMA$)
}

private[client] object JsonCodec extends JsonCodec
