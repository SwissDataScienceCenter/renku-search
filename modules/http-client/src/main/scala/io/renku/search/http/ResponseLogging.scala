package io.renku.search.http

import cats.effect.Sync
import cats.syntax.all.*

import org.http4s.client.UnexpectedStatus
import org.http4s.{Request, Response}
import scribe.mdc.MDC
import scribe.{Level, Scribe}

enum ResponseLogging(level: Option[Level]):
  case Error extends ResponseLogging(Level.Error.some)
  case Warn extends ResponseLogging(Level.Warn.some)
  case Info extends ResponseLogging(Level.Info.some)
  case Debug extends ResponseLogging(Level.Debug.some)
  case Ignore extends ResponseLogging(None)

  def apply[F[_]: Sync](logger: Scribe[F], req: Request[F])(using
      sourcecode.Pkg,
      sourcecode.FileName,
      sourcecode.Name,
      sourcecode.Line
  ): Response[F] => F[Throwable] =
    level match
      case Some(level) => ResponseLogging.log(logger, level, req)
      case None        => r => UnexpectedStatus(r.status, req.method, req.uri).pure[F]

object ResponseLogging:
  def log[F[_]: Sync](
      logger: Scribe[F],
      level: Level,
      req: Request[F]
  )(using
      mdc: MDC,
      pkg: sourcecode.Pkg,
      fileName: sourcecode.FileName,
      name: sourcecode.Name,
      line: sourcecode.Line
  ): Response[F] => F[Throwable] =
    resp =>
      resp.bodyText.compile.string.flatMap { body =>
        logger
          .log(level, mdc, s"Unexpected status ${resp.status}: $body")
          .as(UnexpectedStatus(resp.status, req.method, req.uri))
      }
