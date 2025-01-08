package io.renku.search.provision

import cats.effect.*

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.reindex.ReIndexService
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.solr.client.SearchSolrClient

final case class TestServices(
    pipelineSteps: QueueName => PipelineSteps[IO],
    messageHandlers: MessageHandlers[IO],
    queueClient: QueueClient[IO],
    searchClient: SearchSolrClient[IO],
    backgroundManage: BackgroundProcessManage[IO],
    reindex: ReIndexService[IO]
):
  val reprovision: ReprovisionService[IO] =
    ReprovisionService(reindex, searchClient.underlying)

  def syncHandler(qn: QueueName): SyncMessageHandler[IO] =
    messageHandlers.createHandler(qn)
