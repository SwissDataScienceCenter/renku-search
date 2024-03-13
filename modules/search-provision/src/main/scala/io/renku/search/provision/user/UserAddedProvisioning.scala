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

package io.renku.search.provision.user

import cats.Show
import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all.*
import fs2.io.net.Network
import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1
import io.renku.events.v1.UserAdded
import io.renku.redis.client.QueueName
import io.renku.redis.client.RedisConfig
import io.renku.search.provision.UpsertProvisioningProcess
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

trait UserAddedProvisioning[F[_]] extends UpsertProvisioningProcess[F]

object UserAddedProvisioning:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, UpsertProvisioningProcess[F]] =
    given Scribe[F] = scribe.cats[F]
    UpsertProvisioningProcess.make[F, UserAdded, documents.User](
      queueName,
      redisConfig,
      solrConfig
    )

  private given Show[UserAdded] =
    Show.show[UserAdded](u =>
      u.lastName.map(v => s"lastName '$v'").getOrElse(s"id '${u.id}'")
    )

  private given Transformer[UserAdded, documents.User] =
    _.into[documents.User].transform(
      Field.default(_.score),
      Field.computed(_.name, u => documents.User.nameFrom(u.firstName, u.lastName)),
      Field.default(_.visibility)
    )
