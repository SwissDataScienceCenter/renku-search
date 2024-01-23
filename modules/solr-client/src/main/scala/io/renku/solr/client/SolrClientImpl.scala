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
import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import io.renku.avro.codec.json.{AvroJsonDecoder, AvroJsonEncoder}
import io.renku.solr.client.messages.{InsertResponse, QueryData}
import org.apache.avro.{Schema, SchemaBuilder}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Method, Uri}
import scala.concurrent.duration.Duration

private class SolrClientImpl[F[_]: Async](config: SolrConfig, underlying: Client[F])
    extends SolrClient[F]
    with Http4sClientDsl[F]
    with JsonCodec
    with SolrEntityCodec:
  private[this] val solrUrl: Uri = config.baseUrl / config.core

  override def initialize: F[Unit] =
    ().pure[F]

  def query[A: AvroDecoder](schema: Schema, q: QueryString): F[QueryResponse[A]] =
    val req = Method.POST(
      QueryData(q.q, Nil, q.limit, q.offset, Nil, Map.empty),
      solrUrl / "query"
    )
    given decoder: AvroJsonDecoder[QueryResponse[A]] = QueryResponse.makeDecoder(schema)
    underlying
      .expect[QueryResponse[A]](req)
      .flatTap(r => Async[F].blocking(println(r)))

  def insert[A: AvroEncoder](schema: Schema, docs: Seq[A]): F[InsertResponse] =
    import io.renku.avro.codec.all.given
    given AvroJsonEncoder[Seq[A]] =
      AvroJsonEncoder.create[Seq[A]](SchemaBuilder.array().items(schema))

    given AvroJsonDecoder[InsertResponse] = AvroJsonDecoder.create(InsertResponse.SCHEMA$)
    val req = Method.POST(docs, makeUpdateUrl)
    underlying
      .expect[InsertResponse](req)
      .flatTap(r => Async[F].blocking(println(s"Inserted: $r")))

  private def makeUpdateUrl = {
    val base = solrUrl / "update"
    config.commitWithin match
      case Some(d) if d == Duration.Zero => base.withQueryParam("commit", "true")
      case Some(d) => base.withQueryParam("commitWithin", d.toMillis)
      case None    => base
  }
