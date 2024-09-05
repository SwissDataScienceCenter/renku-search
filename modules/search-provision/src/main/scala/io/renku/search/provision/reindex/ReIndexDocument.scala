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

package io.renku.search.provision.reindex

import java.time.Instant

import cats.Functor
import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.renku.json.codecs.all.given
import io.renku.search.events.MessageId
import io.renku.search.model.Id
import io.renku.solr.client.DocVersion

final private case class ReIndexDocument(
    id: Id,
    created: Instant,
    messageId: Option[MessageId],
    @key("_version_") version: DocVersion
)

private object ReIndexDocument:
  private val docId: Id = Id("reindex_31baded5-9fc2-4935-9b07-80f7a3ecb13f")

  def createNew[F[_]: Clock: Functor](messageId: Option[MessageId]): F[ReIndexDocument] =
    Clock[F].realTimeInstant.map { now =>
      ReIndexDocument(docId, now, messageId, DocVersion.NotExists)
    }

  given Encoder[ReIndexDocument] = MapBasedCodecs.deriveEncoder
  given Decoder[ReIndexDocument] = MapBasedCodecs.deriveDecoder
