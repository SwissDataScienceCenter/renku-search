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

package io.renku.search.solr.documents

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.EncoderSupport

enum DocumentKind:
  case FullEntity
  case PartialEntity

  val name: String = productPrefix.toLowerCase

  private[documents] def additionalField[A]: EncoderSupport.AdditionalFields[A, String] =
    EncoderSupport.AdditionalFields.const[A, String](Fields.kind.name -> name)

object DocumentKind:
  given Encoder[DocumentKind] = Encoder.forString.contramap(_.name)
  given Decoder[DocumentKind] = Decoder.forString.mapEither(fromString)

  def fromString(s: String): Either[String, DocumentKind] =
    DocumentKind.values
      .find(_.name.equalsIgnoreCase(s))
      .toRight(s"Invalid document kind: $s")

  def unsafeFromString(s: String): DocumentKind =
    fromString(s).fold(sys.error, identity)
