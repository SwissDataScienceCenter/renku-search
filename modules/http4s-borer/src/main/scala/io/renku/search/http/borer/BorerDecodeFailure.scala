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

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{Borer, Encoder}
import org.http4s._

final case class BorerDecodeFailure(respString: String, error: Borer.Error[?])
    extends DecodeFailure {

  override val message: String = s"${error.getMessage}: $respString"

  override val cause: Option[Throwable] = Option(error.getCause)

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(status = Status.BadRequest).withEntity(this)
}

object BorerDecodeFailure:
  given Encoder[Borer.Error[?]] = Encoder.forString.contramap(_.getMessage)
  given Encoder[BorerDecodeFailure] = deriveEncoder
  given [F[_]]: EntityEncoder[F, BorerDecodeFailure] =
    BorerEntities.encodeEntityJson[F, BorerDecodeFailure]
