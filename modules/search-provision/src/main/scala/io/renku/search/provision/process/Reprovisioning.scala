package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.provision.reindex.ReprovisionService.ReprovisionRequest

final private[provision] class Reprovisioning[F[_]: Async](
    reprovisionService: ReprovisionService[F]
):
  private val logger = scribe.cats.effect[F]

  // must run reprovisioning in background thread, because this
  // cancels processes where one of them triggered this action. it
  // would result in a deadlock to have it run inside itself

  def processStart(
      msg: EventMessage[ReprovisioningStarted],
      result: Option[Id] => F[Unit]
  ): F[Fiber[F, Throwable, Option[Id]]] =
    Async[F].start {
      ReprovisionRequest.started(msg) match
        case None =>
          logger
            .warn(s"Received reprovision-started message without payload: $msg")
            .flatTap(_ => result(None))
            .as(None)
        case Some(req) =>
          reprovisionService
            .reprovision(req)
            .map {
              case true  => Some(req.reprovisionId)
              case false => None
            }
            .flatTap(result)
    }

  def processFinish(msg: EventMessage[ReprovisioningFinished]): F[Option[Id]] =
    ReprovisionRequest.finished(msg) match
      case None =>
        logger.warn(s"Received reprovision-finish message without payload: $msg").as(None)
      case Some(req) =>
        reprovisionService.reprovision(req).map {
          case true  => Some(req.reprovisionId)
          case false => None
        }
