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

package io.renku.search.http.borer

import cats.effect.Async
import io.bullet.borer.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}

trait BorerEntityCborCodec:
  given [F[_]: Async, A: Decoder]: EntityDecoder[F, A] =
    BorerEntities.decodeEntityCbor[F, A]

  given [F[_], A: Encoder]: EntityEncoder[F, A] =
    BorerEntities.encodeEntityCbor[F, A]

object BorerEntityCborCodec extends BorerEntityCborCodec
