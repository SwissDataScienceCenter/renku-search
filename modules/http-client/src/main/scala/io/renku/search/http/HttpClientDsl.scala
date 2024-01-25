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

import io.renku.search.http.borer.BorerEntityCodec
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, BasicCredentials, Request}

trait HttpClientDsl[F[_]] extends Http4sClientDsl[F] with BorerEntityCodec {

  implicit final class MoreRequestDsl(req: Request[F]) {
    def withBasicAuth(cred: Option[BasicCredentials]): Request[F] =
      cred.map(c => req.putHeaders(Authorization(c))).getOrElse(req)

    def withBearerToken(token: Option[String]): Request[F] =
      token match
        case Some(t) =>
          req.putHeaders(
            Authorization(org.http4s.Credentials.Token(AuthScheme.Bearer, t))
          )
        case None => req
  }
}
