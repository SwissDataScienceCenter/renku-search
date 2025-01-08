package io.renku.search.provision.reindex

import scala.concurrent.duration.*

import cats.Applicative
import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.provision.reindex.ReprovisionService.*
import io.renku.search.provision.reindex.ReprovisionServiceImpl.ReprovisionManageDoc
import io.renku.solr.client.DocVersion
import io.renku.solr.client.SolrClient
import io.renku.solr.client.util.DocumentLockResource
import io.renku.solr.client.util.LockDocument

final class ReprovisionServiceImpl[F[_]: Sync](
    reIndex: ReIndexService[F],
    solrClient: SolrClient[F]
) extends ReprovisionService[F]:

  private val logger = scribe.cats.effect[F]
  private val docId = ReprovisionServiceImpl.docId

  def resetLockDocument: F[Unit] =
    logger.info(s"Reset reprovision lock document $docId") >>
      solrClient.upsertLoop[ReprovisionManageDoc, Unit](docId) { doc =>
        (doc.map(_.copy(isHandling = false)), ())
      }

  def reprovision(req: ReprovisionRequest): F[Result] = req match
    case ReprovisionRequest.FromMessage(id) =>
      logger.info(s"Reprovsion index from message $id") >> reIndex
        .startReIndex(Some(id))

    case ReprovisionRequest.FromLastStart =>
      solrClient
        .findById[ReprovisionManageDoc](docId)
        .map(_.responseBody.docs.headOption)
        .flatMap {
          case None =>
            logger.info("Reprovsion index from the beginning!") >>
              reIndex.startReIndex(None)
          case Some(doc) =>
            logger.info(
              s"Reprovision index from stored message-id ${doc.messageId}"
            ) >> reIndex
              .startReIndex(doc.messageId.some)
        }

    case data: ReprovisionRequest.Started =>
      given LockDocument[F, ReprovisionManageDoc] =
        ReprovisionManageDoc.lockDocument[F](data)

      solrClient
        .findById[ReprovisionManageDoc](docId)
        .map(_.responseBody.docs.headOption)
        .flatMap {
          case Some(d) if d.messageId >= data.messageId =>
            logger
              .info(
                s"Ignore reprovision-started message for a messageId in the past (${data.messageId})"
              )
              .as(false)

          case _ =>
            DocumentLockResource(solrClient).make(docId).use {
              case None =>
                logger
                  .info(
                    "Handling reprovision-started message is already taking place"
                  )
                  .as(false)

              case Some(doc) =>
                for
                  _ <- logger.info(
                    s"Handling reprovision-started message by triggering re-index from message ${data.messageId}"
                  )
                  _ <- reIndex.startReIndex(Some(data.messageId))
                yield true

            }
        }

    case data: ReprovisionRequest.Done =>
      solrClient
        .upsertLoop[ReprovisionManageDoc, Boolean](
          docId,
          interval = 30.millis,
          timeout = 2.minutes
        ) {
          case Some(doc) if data.reprovisionId == doc.reprovisionId =>
            (doc.copy(reprovisionStarted = false).some, true)
          case _ =>
            (None, false)
        }
        .flatMap {
          case true =>
            logger.debug(
              s"ReprovisionManageDoc updated to finished state for ${data.reprovisionId}"
            )
          case false =>
            logger
              .info(
                s"Ignore reprovision-finish message for unknown id (${data.reprovisionId})"
              )
        }
        .attempt
        .flatMap {
          case Left(ex) =>
            logger
              .warn(
                "Error updating finished state on reprovision document ${data.reprovisionId}",
                ex
              )
              .as(false)

          case Right(_) => true.pure[F]
        }

object ReprovisionServiceImpl:
  private[reindex] val docId = "REPROV_MSG_0f8f7edc-ddc6-45aa-8655-4494d063c8d9"

  final case class ReprovisionManageDoc(
      id: String,
      @key("message_id_s") messageId: MessageId,
      @key("reprovision_id_s") reprovisionId: Id,
      @key("reprovision_started_b") reprovisionStarted: Boolean,
      @key("is_handling_b") isHandling: Boolean,
      @key("_version_") version: DocVersion
  )

  object ReprovisionManageDoc {
    given Encoder[ReprovisionManageDoc] = MapBasedCodecs.deriveEncoder
    given Decoder[ReprovisionManageDoc] = MapBasedCodecs.deriveDecoder

    def lockDocument[F[_]: Applicative](
        msg: ReprovisionMessageData
    ): LockDocument[F, ReprovisionManageDoc] =
      LockDocument(
        !_.isHandling,
        (d, id) =>
          ReprovisionManageDoc(
            d.map(_.id).getOrElse(id),
            msg.messageId,
            msg.reprovisionId,
            msg.isStarted,
            true,
            d.map(_.version).getOrElse(DocVersion.NotExists)
          ).pure[F],
        _.copy(isHandling = false).some
      )
  }
