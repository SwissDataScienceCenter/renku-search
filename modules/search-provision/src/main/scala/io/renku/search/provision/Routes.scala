package io.renku.search.provision

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
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

final class Routes[F[_]: Async](
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

object Routes:
  enum Paths {
    case Reindex
    lazy val name: String = productPrefix.toLowerCase()
  }

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
    given Encoder[ReIndexMessage] = MapBasedCodecs.deriveEncoder
