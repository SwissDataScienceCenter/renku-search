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

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder}

final case class Field(
    name: FieldName,
    @key("type") typeName: TypeName,
    required: Boolean = false,
    indexed: Boolean = true,
    stored: Boolean = true,
    multiValued: Boolean = false,
    uninvertible: Boolean = true,
    docValues: Boolean = false
):
  def makeMultiValued: Field = copy(multiValued = true)

object Field:
  def apply(name: FieldName, fieldType: FieldType): Field =
    apply(name, fieldType.name)

  given Encoder[Field] = MapBasedCodecs.deriveEncoder
  given Decoder[Field] = MapBasedCodecs.deriveDecoder
