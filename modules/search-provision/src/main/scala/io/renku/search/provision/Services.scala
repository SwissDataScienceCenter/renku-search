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
import io.renku.search.sentry.Sentry
import io.renku.search.sentry.TagName
import io.renku.search.sentry.TagValue
import io.renku.search.solr.client.SearchSolrClient

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
      handlers = MessageHandlers[F](steps, rps, sentry, cfg.queuesConfig, ctrl)
    } yield Services(cfg, solr, redis, handlers, bm, rps, ris, sentry)
