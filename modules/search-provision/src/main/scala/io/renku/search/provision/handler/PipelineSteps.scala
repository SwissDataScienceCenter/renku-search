package io.renku.search.provision.handler

import cats.effect.Async
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName
import io.renku.search.solr.client.SearchSolrClient

trait PipelineSteps[F[_]]:
  def reader: MessageReader[F]
  def pushToSolr: PushToSolr[F]
  def fetchFromSolr: FetchFromSolr[F]
  def deleteFromSolr: DeleteFromSolr[F]
  def userUtils: UserUtils[F]
  def solrClient: SearchSolrClient[F]

object PipelineSteps:
  def apply[F[_]: Async](
      searchSolrClient: SearchSolrClient[F],
      queueClient: Stream[F, QueueClient[F]],
      inChunkSize: Int
  )(
      queue: QueueName
  ): PipelineSteps[F] =
    new PipelineSteps[F] {
      val solrClient = searchSolrClient
      val reader: MessageReader[F] =
        MessageReader[F](queueClient, queue, inChunkSize)
      val pushToSolr: PushToSolr[F] =
        PushToSolr[F](solrClient, reader)
      val fetchFromSolr: FetchFromSolr[F] =
        FetchFromSolr[F](solrClient)
      val deleteFromSolr: DeleteFromSolr[F] =
        DeleteFromSolr[F](solrClient, reader)
      val userUtils: UserUtils[F] =
        UserUtils[F](fetchFromSolr, pushToSolr, reader, 200)
    }
