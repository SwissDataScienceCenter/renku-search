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

import scribe.Scribe

final class LoggerProxy[F[_]](logger: Scribe[F])
    extends org.typelevel.log4cats.Logger[F] {
  override def error(message: => String): F[Unit] = logger.error(message)

  override def warn(message: => String): F[Unit] = logger.warn(message)

  override def info(message: => String): F[Unit] = logger.info(message)

  override def debug(message: => String): F[Unit] = logger.debug(message)

  override def trace(message: => String): F[Unit] = logger.trace(message)

  override def error(t: Throwable)(message: => String): F[Unit] = logger.error(message, t)

  override def warn(t: Throwable)(message: => String): F[Unit] = logger.warn(message, t)

  override def info(t: Throwable)(message: => String): F[Unit] = logger.info(message, t)

  override def debug(t: Throwable)(message: => String): F[Unit] = logger.debug(message, t)

  override def trace(t: Throwable)(message: => String): F[Unit] = logger.trace(message, t)
}

object LoggerProxy:
  def apply[F[_]](delegate: Scribe[F]): org.typelevel.log4cats.Logger[F] =
    new LoggerProxy[F](delegate)
