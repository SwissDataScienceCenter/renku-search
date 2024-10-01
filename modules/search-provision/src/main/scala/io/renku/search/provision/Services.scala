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

import cats.NonEmptyParallel
import cats.effect.kernel.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network

import io.renku.queue.client.QueueClient
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.reindex.ReIndexService
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.sentry.Sentry
import io.renku.search.sentry.TagName
import io.renku.search.sentry.TagValue

final case class Services[F[_]](
    config: SearchProvisionConfig,
    solrClient: SearchSolrClient[F],
    queueClient: Stream[F, QueueClient[F]],
    messageHandlers: MessageHandlers[F],
    backgroundManage: BackgroundProcessManage[F],
    reprovision: ReprovisionService[F],
    reindex: ReIndexService[F],
    sentry: Sentry[F]
):

  def resetLockDocuments(using NonEmptyParallel[F]): F[Unit] =
    (reprovision.resetLockDocument, reindex.resetLockDocument).parMapN((_, _) => ())

object Services:

  def make[F[_]: Async: Network]: Resource[F, Services[F]] =
    for {
      cfg <- Resource.eval(SearchProvisionConfig.config.load[F])
      solr <- SearchSolrClient.make[F](cfg.solrConfig)

      // The redis client is refreshed every now and then so it's provided as a stream
      redis = QueueClient.stream[F](cfg.redisConfig, cfg.clientId)

      steps = PipelineSteps[F](
        solr,
        redis,
        inChunkSize = 1
      )
      bm <- BackgroundProcessManage[F](cfg.retryOnErrorDelay)
      ris = ReIndexService[F](bm, redis, solr, cfg.queuesConfig)
      rps = ReprovisionService(ris, solr.underlying)
      ctrl <- Resource.eval(SyncMessageHandler.Control[F])
      sentry <- Sentry[F](
        cfg.sentryConfig.withTag(TagName.service, TagValue.searchProvision)
      )
      handlers = MessageHandlers[F](steps, rps, cfg.queuesConfig, ctrl)
    } yield Services(cfg, solr, redis, handlers, bm, rps, ris, sentry)
