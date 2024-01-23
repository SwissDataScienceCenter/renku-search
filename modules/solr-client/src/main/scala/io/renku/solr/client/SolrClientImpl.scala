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

package io.renku.solr.client

import cats.effect.Async
import cats.syntax.all.*
import io.renku.solr.client.messages.QueryData
import org.http4s.{Method, Uri}
import org.http4s.Method.POST
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

private class SolrClientImpl[F[_]: Async](config: SolrConfig, underlying: Client[F])
    extends SolrClient[F]
    with Http4sClientDsl[F]
    with JsonCodec
    with SolrEntityCodec:
  private[this] val solrUrl: Uri = config.baseUrl / config.core

  override def initialize: F[Unit] =
    ().pure[F]

  override def query(q: QueryString): F[Unit] =
    val req = Method.POST(
      QueryData(q.q, Nil, q.limit, q.offset, Nil, Map.empty),
      solrUrl / "query"
    )
    underlying
      .expect[String](req)
      .flatMap(r => Async[F].blocking(println(r)))
      .void
