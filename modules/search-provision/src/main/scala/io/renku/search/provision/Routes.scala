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

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.events.MessageId
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.provision.reindex.ReprovisionService.ReprovisionRequest
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

final private class Routes[F[_]: Async](
    metricsRoutes: HttpRoutes[F],
    services: Services[F]
) extends Http4sDsl[F]
    with BorerEntityJsonCodec:

  private lazy val operationRoutes =
    Router[F](
      "/reindex" -> reIndexRoutes,
      "/" -> OperationRoutes[F]
    )

  lazy val routes: HttpRoutes[F] =
    operationRoutes <+> metricsRoutes

  def reIndexRoutes: HttpRoutes[F] = HttpRoutes.of { case req @ POST -> Root =>
    req.as[Routes.ReIndexMessage].flatMap { msg =>
      services.reprovision.reprovision(msg.toRequest).flatMap {
        case true  => NoContent()
        case false => UnprocessableEntity()
      }
    }
  }

private object Routes:
  def apply[F[_]: Async: Network](
      registryBuilder: CollectorRegistryBuilder[F],
      services: Services[F]
  ): Resource[F, HttpRoutes[F]] =
    MetricsRoutes[F](registryBuilder).makeRoutes
      .map(new Routes[F](_, services).routes)

  final case class ReIndexMessage(messageId: Option[MessageId] = None):
    def toRequest: ReprovisionRequest = messageId
      .map(ReprovisionRequest.forSpecificMessage)
      .getOrElse(ReprovisionRequest.lastStart)
  object ReIndexMessage:
    given Decoder[ReIndexMessage] = MapBasedCodecs.deriveDecoder
