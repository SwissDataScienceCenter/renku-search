package io.renku.search.provision.reindex

import cats.effect.kernel.Sync
import cats.syntax.all.*

import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.provision.reindex.ReprovisionService.{ReprovisionRequest, Result}
import io.renku.solr.client.SolrClient

trait ReprovisionService[F[_]]:
  def reprovision(req: ReprovisionRequest): F[Result]
  def recreateIndex: F[Result] = reprovision(ReprovisionRequest.lastStart)
  def resetLockDocument: F[Unit]

object ReprovisionService:
  def apply[F[_]: Sync](
      reIndex: ReIndexService[F],
      solrClient: SolrClient[F]
  ): ReprovisionService[F] =
    ReprovisionServiceImpl(reIndex, solrClient)

  type Result = Boolean

  sealed trait ReprovisionRequest
  sealed trait ReprovisionMessageData extends ReprovisionRequest:
    def messageId: MessageId
    def reprovisionId: Id
    def isStarted: Boolean

  object ReprovisionRequest {
    final case class Started(
        messageId: MessageId,
        reprovisionId: Id
    ) extends ReprovisionMessageData:
      val isStarted = true

    final case class Done(
        messageId: MessageId,
        reprovisionId: Id
    ) extends ReprovisionMessageData:
      val isStarted = false

    case object FromLastStart extends ReprovisionRequest

    final case class FromMessage(messageId: MessageId) extends ReprovisionRequest

    def started(
        msg: EventMessage[ReprovisioningStarted]
    ): Option[ReprovisionMessageData] =
      msg.payload.headOption.map(p => Started(msg.id, p.id))
    def finished(
        msg: EventMessage[ReprovisioningFinished]
    ): Option[ReprovisionMessageData] =
      msg.payload.headOption.map(p => Done(msg.id, p.id))

    def lastStart: ReprovisionRequest = FromLastStart
    def forSpecificMessage(id: MessageId): ReprovisionRequest = FromMessage(id)
  }
