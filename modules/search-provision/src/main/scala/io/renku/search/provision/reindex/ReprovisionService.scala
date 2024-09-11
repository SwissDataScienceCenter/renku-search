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

/*

re-index is the unconditional reset, reprovision is more controled

reprovisioning can happen in these cases:
 - manual request: get the latest "beginning" message id and do reindex(messageId)
 - manual request with a messageId: just call reindex(messageId) - or leave this to reindex?
 - reprovsion-start message: validate + store and do reset_index(newMessageId)
 - reprovision-finish message: validate + set flag to false

 */

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
