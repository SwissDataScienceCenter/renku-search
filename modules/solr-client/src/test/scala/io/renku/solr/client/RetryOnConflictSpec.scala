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

import cats.effect.*

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder}
import io.renku.solr.client.RetryOnConflictSpec.Data.*
import io.renku.solr.client.UpsertResponse.syntax.*
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.SolrClientBaseSuite
import munit.CatsEffectSuite
import scribe.Scribe

class RetryOnConflictSpec extends CatsEffectSuite with SolrClientBaseSuite:
  private val logger: Scribe[IO] = scribe.cats.io

  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClient)

  private val migrations = Seq(
    SchemaCommand.Add(
      FieldType.text(TypeName("my_text_field")).withAnalyzer(Analyzer.defaultSearch)
    ),
    SchemaCommand.Add(Field(FieldName("name"), TypeName("my_text_field")))
  )

  private def truncate(client: SolrClient[IO]): IO[Unit] =
    truncateQuery(client)(
      SearchCaseInsensitiveSpec.idQuery,
      Seq(FieldName("name")),
      Seq(TypeName("my_text_field"))
    )

  private def createClient: IO[SolrClient[IO]] =
    for
      client <- IO(solrClient())
      _ <- truncate(client)
      _ <- client.modifySchema(migrations)
    yield client

  test("succeed eventually when creating a document"):
    for
      client <- createClient
      attempts <- Ref.of[IO, Int](3)
      task = attempts
        .updateAndGet(_ - 1)
        .map { n =>
          if (n <= 0) pogi.copy(version = DocVersion.NotExists) // success
          else pogi.copy(version = DocVersion.Exact(1)) // fail
        }
        .flatMap(p => client.upsert(Seq(p)))

      taskWithRetry = task.retryOnConflict(5)
      res <- taskWithRetry
      _ = assert(res.isSuccess)
      _ <- assertIO(attempts.get, 0)
    yield ()

  test("fail when retries are exhausted"):
    for
      client <- createClient
      attempts <- Ref.of[IO, Int](10)
      task = attempts
        .updateAndGet(_ - 1)
        .map { n =>
          if (n <= 0) remco.copy(version = DocVersion.NotExists) // success
          else remco.copy(version = DocVersion.Exact(1)) // fail
        }
        .flatMap(p => client.upsert(Seq(p)))

      taskWithRetry = task.retryOnConflict(3)
      res <- taskWithRetry
      _ = assert(!res.isSuccess)
      _ <- assertIO(attempts.get, 6)
    yield ()

  test("success on first try"):
    for
      client <- createClient
      attempts <- Ref.of[IO, Int](10)
      task = attempts
        .updateAndGet(_ - 1)
        .map { _ =>
          hirschi.copy(version = DocVersion.NotExists) // success
        }
        .flatMap(p => client.upsert(Seq(p)))

      resp <- task.retryOnConflict(5)
      _ = assert(resp.isSuccess)
      _ = assertIO(attempts.get, 10)
    yield ()

object RetryOnConflictSpec:

  object Data {
    final case class Person(
        id: String,
        name: String,
        @key("year_i") year: Int,
        @key("_version_") version: DocVersion = DocVersion.Off
    ):
      def withVersion(n: Long): Person = copy(version = DocVersion.Exact(n))

    object Person:
      given Encoder[Person] = MapBasedCodecs.deriveEncoder
      given Decoder[Person] = MapBasedCodecs.deriveDecoder

    val pogi = Person("p1", "Tadej Pogačar", 1998)
    val remco = Person("p2", "Remco Evenepoel", 2000)
    val hirschi = Person("p3", "Marc Hirschi", 1998)
  }
