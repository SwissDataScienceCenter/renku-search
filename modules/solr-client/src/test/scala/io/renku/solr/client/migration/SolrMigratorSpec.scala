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

package io.renku.solr.client.migration

import cats.effect.IO

import io.renku.solr.client.SolrClient
import io.renku.solr.client.schema.*
import io.renku.solr.client.schema.SchemaCommand.Add
import io.renku.solr.client.util.SolrClientBaseSuite

class SolrMigratorSpec extends SolrClientBaseSuite:
  private val logger = scribe.cats.io

  private val migrations = Seq(
    SchemaMigration(-5, Add(FieldType.text(TypeName("testText"), Analyzer.classic))),
    SchemaMigration(-4, Add(FieldType.int(TypeName("testInt")))),
    SchemaMigration(-3, Add(Field(FieldName("testName"), TypeName("testText")))),
    SchemaMigration(-2, Add(Field(FieldName("testDescription"), TypeName("testText")))),
    SchemaMigration(-1, Add(Field(FieldName("testSeats"), TypeName("testInt"))))
  )

  private def truncate(client: SolrClient[IO]): IO[Unit] =
    truncateAll(client)(
      Seq(
        FieldName("currentSchemaVersion"),
        FieldName("testName"),
        FieldName("testDescription"),
        FieldName("testSeats")
      ),
      Seq(TypeName("testText"), TypeName("testInt"))
    )

  test("run sample migrations") {
    for {
      client <- IO(solrClient())
      migrator = SchemaMigrator[IO](client)
      _ <- truncate(client)
      _ <- migrator.migrate(migrations)
      c <- migrator.currentVersion
      _ = assertEquals(c, Some(-1L))
    } yield ()
  }

  test("run migrations"):
    for {
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      first = migrations.take(2)

      _ <- truncate(client)
      _ <- migrator.migrate(first)
      v0 <- migrator.currentVersion
      _ = assertEquals(v0, Some(-4L))

      _ <- migrator.migrate(migrations)
      v1 <- migrator.currentVersion
      _ = assertEquals(v1, Some(-1L))
    } yield ()
