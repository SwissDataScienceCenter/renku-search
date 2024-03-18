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

package io.renku.search.perftests

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network
import io.bullet.borer.Decoder
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.borer.BorerEntityJsonCodec.given
import io.renku.search.model.users
import org.http4s.MediaType.application
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Accept
import org.http4s.implicits.*
import org.http4s.{Header, MediaType, Method, Uri}
import org.typelevel.ci.*

object RandommerIoClient:
  def make[F[_]: Async: Network](apiKey: String): Resource[F, RandomDataFetcher[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map(new RandommerIoClient[F](_, apiKey))

private class RandommerIoClient[F[_]: Async](
    client: Client[F],
    apiKey: String,
    chunkSize: Int = 20
) extends RandomDataFetcher[F]
    with HttpClientDsl[F]:

  override def findNames: Stream[F, (users.FirstName, users.LastName)] =
    Stream.evals {
      val req = get(
        (api / "name")
          .withQueryParam("nameType", "fullname")
          .withQueryParam("quantity", chunkSize)
      )
      client.expect[List[String]](req).map(_.flatMap(toFirstAndLast))
    } ++ findNames

  private lazy val api = uri"https://randommer.io/api"

  private def get(uri: Uri) =
    GET(uri)
      .putHeaders(Accept(application.json))
      .putHeaders(Header.Raw(ci"X-Api-Key", apiKey))

  private def toFirstAndLast(v: String): Option[(users.FirstName, users.LastName)] =
    v.split(' ').toList match {
      case f :: r => Some(users.FirstName(f) -> users.LastName(r.mkString(" ")))
      case _      => None
    }
