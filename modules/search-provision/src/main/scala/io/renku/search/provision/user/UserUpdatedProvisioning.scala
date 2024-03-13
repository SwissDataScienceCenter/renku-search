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
import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1.UserUpdated
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.{Id, Name, users}
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

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

  private given Show[UserUpdated] =
    Show.show[UserUpdated](u => s"id '${u.id}'")

  private lazy val idExtractor: UserUpdated => Id = uu => Id(uu.id)

  private lazy val docUpdate: ((UserUpdated, documents.User)) => documents.User = {
    case (update, _) =>
      update
        .into[documents.User]
        .transform(
          Field.default(_.score),
          Field.computed(_.name, u => documents.User.nameFrom(u.firstName, u.lastName))
        )
  }
