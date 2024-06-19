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

package io.renku.search.api.routes

import cats.effect.*

import io.renku.search.api.tapir.ApiSchema
import io.renku.search.http.borer.TapirBorerJson
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.AnyEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.cors.CORSInterceptor

trait RoutesDefinition[F[_]] extends ApiSchema with TapirBorerJson:
  def routes: HttpRoutes[F]
  def docRoutes: List[AnyEndpoint]

  extension [A, B, C, D, E](self: Endpoint[A, B, C, D, E])
    def in(path: List[String]) = path match
      case Nil     => self
      case n :: nn => self.in(nn.foldLeft(n: EndpointInput[Unit])(_ / _))

object RoutesDefinition:
  def interpreter[F[_]: Async] = Http4sServerInterpreter[F](
    Http4sServerOptions.customiseInterceptors
      .corsInterceptor(CORSInterceptor.default)
      .options
  )
