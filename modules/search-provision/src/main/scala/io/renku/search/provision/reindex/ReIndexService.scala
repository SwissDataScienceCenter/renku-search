package io.renku.search.provision.reindex

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.search.config.QueuesConfig
import io.renku.search.events.MessageId
import io.renku.search.provision.BackgroundProcessManage
import io.renku.search.provision.MessageHandlers.MessageHandlerKey
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.util.*

trait ReIndexService[F[_]]:
  /** Stops background processes handling redis messages, drop the index and then restarts
    * background processes. The `startMessage` can specify from which point to start
    * reading the stream again. If it is `None` the stream is read from the beginning.
    *
    * This method ensures that only one re-indexing is initiated at a time.
    */
  def startReIndex(startMessage: Option[MessageId]): F[Boolean]

  /** Drops the SOLR index and marks the new start of the redis stream according to
    * `startMessage`. If `startMessage` is `None`, the stream will be read from the
    * beginning.
    *
    * NOTE: This method assumes no handlers currently reading from the redis streams.
    */
  def resetData(startMessage: Option[MessageId]): F[Unit]

  def resetLockDocument: F[Unit]

object ReIndexService:
  private[reindex] val lockId: String = "reindex_31baded5-9fc2-4935-9b07-80f7a3ecb13f"

  def apply[F[_]: Clock: Async](
      bpm: BackgroundProcessManage[F],
      redisClient: Stream[F, QueueClient[F]],
      solrClient: SearchSolrClient[F],
      queueCfg: QueuesConfig
  ): ReIndexService[F] =
    new ReIndexService[F] {
      private val queueName = NonEmptyList.of(queueCfg.dataServiceAllEvents)
      private val logger = scribe.cats.effect[F]

      def resetLockDocument: F[Unit] =
        logger.info(s"Reset reindex lock document $lockId") >>
          solrClient.underlying.deleteIds(NonEmptyList.of(lockId))

      def startReIndex(startMessage: Option[MessageId]): F[Boolean] =
        given LockDocument[F, ReIndexDocument] =
          ReIndexDocument.lockDocument(startMessage)
        val lock = solrClient.underlying.lockBy[ReIndexDocument](lockId)
        lock.use {
          case None =>
            logger.debug(s"Re-Index called while already in progress").as(false)
          case Some(d) => dropIndexAndRestart(d, startMessage).as(true)
        }

      def resetData(startMessage: Option[MessageId]): F[Unit] =
        for
          _ <- startMessage match
            case Some(msgId) =>
              logger.info(s"Set last seen message id to $msgId for $queueName") >>
                redisClient
                  .evalMap(_.markProcessed(queueName, msgId))
                  .take(1)
                  .compile
                  .drain
            case None =>
              // a special case until the other queues are removed and
              // only `data_service.all_events` is left. we remove the
              // last seen message-ids from all queue names, in
              // contrast for setting a message-id which can only be
              // done for one queue (and doesn't make sense to
              // implement for all)
              logger.info(s"Remove last processed message id for ${queueCfg.all}") >>
                redisClient
                  .evalMap(rc =>
                    queueCfg.all.toList.traverse_(q =>
                      rc.removeLastProcessed(NonEmptyList.of(q))
                    )
                  )
                  .take(1)
                  .compile
                  .drain
          _ <- logger.info("Delete SOLR index")
          _ <- solrClient.deletePublicData
        yield ()

      private def dropIndexAndRestart(
          syncDoc: ReIndexDocument,
          startMessage: Option[MessageId]
      ) =
        for
          _ <- logger.info(
            s"Starting re-indexing all data, since message ${syncDoc.messageId}"
          )
          _ <- bpm.cancelProcesses(MessageHandlerKey.isInstance)
          _ <- logger.info("Background processes stopped")
          _ <- resetData(startMessage)
          _ <- logger.info("Start background processes")
          _ <- bpm.background(MessageHandlerKey.isInstance)
        yield ()
    }
