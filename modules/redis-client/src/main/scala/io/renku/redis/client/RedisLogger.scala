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

package io.renku.redis.client

import dev.profunktor.redis4cats.effect.Log
import scribe.Scribe

class RedisLogger[F[_]](scribe: Scribe[F]) extends Log[F]:

  override def debug(msg: => String): F[Unit] =
    scribe.debug(msg)

  override def info(msg: => String): F[Unit] =
    scribe.info(msg)

  override def error(msg: => String): F[Unit] =
    scribe.error(msg)

object RedisLogger:
  def apply[F[_]: Scribe]: Log[F] = new RedisLogger[F](Scribe[F])
