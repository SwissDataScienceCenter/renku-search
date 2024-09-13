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

import cats.effect.*

import io.prometheus.client.Counter
import io.renku.search.events.MsgType
import io.renku.search.events.SyncEventMessage
import io.renku.search.metrics.Collector
import io.renku.search.provision.handler.DeleteFromSolr
import io.renku.solr.client.UpsertResponse

object MessageMetrics:
  def countReceived[F[_]: Sync](msg: SyncEventMessage): F[Unit] =
    ReceivedMessageCounter.increment(msg.header.msgType)

  def countResult[F[_]: Sync](
      msg: SyncEventMessage,
      result: SyncMessageHandler.Result
  ): F[Unit] =
    val mt = msg.header.msgType
    result match
      case SyncMessageHandler.Result.Upsert(UpsertResponse.Success(_)) =>
        SuccessMessageCounter.increment(mt)
      case SyncMessageHandler.Result.Upsert(UpsertResponse.VersionConflict) =>
        FailedMessageCounter.increment(mt)

      case SyncMessageHandler.Result.Delete(DeleteFromSolr.DeleteResult.Success(_)) =>
        SuccessMessageCounter.increment(mt)
      case SyncMessageHandler.Result.Delete(DeleteFromSolr.DeleteResult.NoIds(_)) =>
        IgnoredMessageCounter.increment(mt)
      case SyncMessageHandler.Result.Delete(DeleteFromSolr.DeleteResult.Failed(_, _)) =>
        FailedMessageCounter.increment(mt)

      case SyncMessageHandler.Result.ReprovisionStart(Some(_)) =>
        SuccessMessageCounter.increment(mt)
      case SyncMessageHandler.Result.ReprovisionStart(None) =>
        IgnoredMessageCounter.increment(mt)

      case SyncMessageHandler.Result.ReprovisionFinish(Some(_)) =>
        SuccessMessageCounter.increment(mt)
      case SyncMessageHandler.Result.ReprovisionFinish(None) =>
        IgnoredMessageCounter.increment(mt)

  def all: List[MessageCounter] =
    List(
      ReceivedMessageCounter,
      SuccessMessageCounter,
      FailedMessageCounter,
      IgnoredMessageCounter
    )

  trait MessageCounter extends Collector:
    def asJCollector: Counter
    def increment[F[_]](mt: MsgType)(using Sync[F]): F[Unit] =
      Sync[F].blocking(asJCollector.labels(mkLabel(mt)).inc())
    private def mkLabel(mt: MsgType): String =
      mt.name.replace('.', '_')

  object ReceivedMessageCounter extends Collector with MessageCounter:
    private val underlying =
      Counter
        .build()
        .name("received_messages")
        .help("Total number of received messages")
        .labelNames("type")
        .create()
    val asJCollector: Counter = underlying

  object SuccessMessageCounter extends Collector with MessageCounter:
    private val underlying =
      Counter
        .build()
        .name("success_messages")
        .help("Total number of received messages that have been processed successfully")
        .labelNames("type")
        .create()
    val asJCollector: Counter = underlying

  object FailedMessageCounter extends Collector with MessageCounter:
    private val underlying =
      Counter
        .build()
        .name("failed_messages")
        .labelNames("type")
        .help(
          "Total number of received messages that have been processed, but resulted in an error"
        )
        .create()
    val asJCollector: Counter = underlying

  object IgnoredMessageCounter extends Collector with MessageCounter:
    private val underlying =
      Counter
        .build()
        .name("ignored_messages")
        .labelNames("type")
        .help(
          "Total number of received messages that have been received, but ignored for processing"
        )
        .create()
    val asJCollector: Counter = underlying
