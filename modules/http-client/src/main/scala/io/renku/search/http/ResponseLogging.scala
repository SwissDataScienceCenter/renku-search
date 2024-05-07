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
