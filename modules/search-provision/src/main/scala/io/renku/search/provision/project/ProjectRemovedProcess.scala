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

package io.renku.search.provision.project

import cats.Show
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network
import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1
import io.renku.events.v1.ProjectRemoved
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.Id
import io.renku.search.provision.SolrRemovalProcess
import io.renku.solr.client.SolrConfig
import scribe.Scribe

object ProjectRemovedProcess:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, SolrRemovalProcess[F]] =
    given Scribe[F] = scribe.cats[F]
    SolrRemovalProcess.make[F, ProjectRemoved](
      queueName,
      ProjectRemoved.SCHEMA$,
      redisConfig,
      solrConfig,
      onSolrPersist = None
    )

  private given Show[ProjectRemoved] =
    Show.show[ProjectRemoved](pr => show"slug '${pr.id}'")

  private given Transformer[ProjectRemoved, Id] =
    r => Id(r.id)