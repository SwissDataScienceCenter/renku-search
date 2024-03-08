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

package io.renku.search.provision
package user

import cats.Show
import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.bullet.borer.Codec.*
import io.bullet.borer.{Codec, Decoder, Encoder}
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1.UserUpdated
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.users
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

trait UserUpdatedProvisioning[F[_]] extends UpdateProvisioningProcess[F]

object UserUpdatedProvisioning:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, UpdateProvisioningProcess[F]] =
    given Scribe[F] = scribe.cats[F]
    UpdateProvisioningProcess.make[F, UserUpdated, documents.User](
      queueName,
      UserUpdated.SCHEMA$,
      idExtractor,
      docUpdate,
      redisConfig,
      solrConfig
    )

  private given Codec[documents.User] = Codec[documents.User](
    Encoder[documents.Entity].contramap(_.asInstanceOf[documents.Entity]),
    Decoder[documents.Entity].mapEither {
      case u: documents.User => Right(u)
      case u                 => Left(s"${u.getClass} is not a User document")
    }
  )

  private given Show[UserUpdated] =
    Show.show[UserUpdated](u => s"id '${u.id}'")

  private lazy val idExtractor: UserUpdated => String = _.id

  private lazy val docUpdate: ((UserUpdated, documents.User)) => documents.User = {
    case (update, origDoc) =>
      val doc1 = update.firstName
        .fold(origDoc)(v => origDoc.copy(firstName = Some(users.FirstName(v))))
      val doc2 = update.lastName
        .fold(doc1)(v => origDoc.copy(lastName = Some(users.LastName(v))))
      update.email
        .fold(doc2)(v => origDoc.copy(email = Some(users.Email(v))))
  }
