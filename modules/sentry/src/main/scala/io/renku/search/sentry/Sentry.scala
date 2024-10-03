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

package io.renku.search.sentry

import cats.Applicative
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import io.sentry.Sentry as JSentry

trait Sentry[F[_]]:
  def capture(ev: SentryEvent): F[Unit]

object Sentry:

  def noop[F[_]: Applicative]: Sentry[F] =
    new Sentry[F] {
      def capture(ev: SentryEvent): F[Unit] = ().pure[F]
    }

  def stdout[F[_]: Console]: Sentry[F] =
    new Sentry[F] {
      def capture(ev: SentryEvent): F[Unit] = Console[F].println(ev.toString)
    }

  def apply[F[_]: Sync](cfg: SentryConfig): Resource[F, Sentry[F]] =
    cfg.toSentryOptions match
      case None => Resource.pure(noop[F])
      case Some(opts) =>
        Resource.make(Sync[F].blocking {
          // TODO figure out if there is a better way than this once-per-jvm static
          JSentry.init(opts)
          new Impl[F]
        })(_ => Sync[F].blocking(JSentry.close))

  final private class Impl[F[_]: Sync] extends Sentry[F] {
    def capture(ev: SentryEvent): F[Unit] =
      Sync[F].blocking(JSentry.captureEvent(ev.toEvent)).void
  }
