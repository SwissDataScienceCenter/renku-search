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

import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.http.{ClientBuilder, ResponseLogging, RetryConfig}
import io.renku.solr.client.schema.SchemaCommand
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.client.EmberClientBuilder.default
import cats.data.NonEmptyList

trait SolrClient[F[_]]:
  def modifySchema(
      cmds: Seq[SchemaCommand],
      onErrorLog: ResponseLogging = ResponseLogging.Error
  ): F[Unit]

  def query[A: Decoder](q: QueryString): F[QueryResponse[A]]

  def query[A: Decoder](q: QueryData): F[QueryResponse[A]]

  def delete(q: QueryString): F[Unit]
  def deleteIds(ids: NonEmptyList[String]): F[Unit]

  def upsert[A: Encoder](docs: Seq[A]): F[InsertResponse]

  def findById[A: Decoder](id: String, other: String*): F[GetByIdResponse[A]]

object SolrClient:
  def apply[F[_]: Async: Network](config: SolrConfig): Resource[F, SolrClient[F]] =
    ClientBuilder(EmberClientBuilder.default[F])
      .withDefaultRetry(RetryConfig.default)
      .withLogging(logBody = config.logMessageBodies, scribe.cats.effect[F])
      .build
      .map(new SolrClientImpl[F](config, _))
