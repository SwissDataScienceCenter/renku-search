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

import io.renku.solr.client.DocVersion
import io.renku.solr.client.SolrClient
import io.renku.solr.client.schema.*
import io.renku.solr.client.schema.SchemaCommand.Add
import io.renku.solr.client.util.SolrClientBaseSuite
import munit.CatsEffectSuite

class SolrMigratorSpec extends CatsEffectSuite with SolrClientBaseSuite:
  private val logger = scribe.cats.io

  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClient)

  private val migrations = Seq(
    SchemaMigration(
      -5,
      Add(FieldType.text(TypeName("testText")).withAnalyzer(Analyzer.classic))
    ),
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
      res <- migrator.migrate(migrations)
      c <- migrator.currentVersion
      _ = assertEquals(c, Some(-1L))
      _ = assertEquals(res, MigrateResult(None, Some(-1L), migrations.size, false))
    } yield ()
  }

  test("run migrations"):
    for {
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      first = migrations.take(2)

      _ <- truncate(client)
      res0 <- migrator.migrate(first)
      v0 <- migrator.currentVersion
      _ = assertEquals(v0, Some(-4L))
      _ = assertEquals(res0, MigrateResult(None, Some(-4L), 2, false))

      res1 <- migrator.migrate(migrations)
      v1 <- migrator.currentVersion
      _ = assertEquals(v1, Some(-1L))
      _ = assertEquals(res1, MigrateResult(Some(-4L), Some(-1L), 3, false))
    } yield ()

  test("no require-reindex if migrations have been applied already"):
    val migs = migrations.head.withRequiresReIndex +: migrations.tail
    for
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      first = migs.take(2)
      _ <- truncate(client)

      res0 <- migrator.migrate(first)
      _ = assertEquals(res0, MigrateResult(None, Some(-4L), 2, true))

      res1 <- migrator.migrate(migs)
      _ = assertEquals(res1, MigrateResult(Some(-4L), Some(-1L), 3, false))
    yield ()

  test("convert previous version document to current, then migrate remaining"):
    for
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      _ <- truncate(client)
      _ <- client.modifySchema(
        Seq(
          Add(Field(FieldName("currentSchemaVersion"), TypeName("plong"))),
          Add(FieldType.text(TypeName("testText")).withAnalyzer(Analyzer.classic)),
          Add(FieldType.int(TypeName("testInt")))
        )
      )

      oldDoc = VersionDocument.HistoricDocument1(
        SchemaMigrator.versionDocId,
        Some(-3),
        DocVersion.NotExists
      )
      _ <- client.upsertSuccess(Seq(oldDoc))

      res <- migrator.migrate(migrations)
      _ = assert(res.migrationsRun > 0, s"migrationsRun: ${res.migrationsRun}")

      doc <- client
        .findById[VersionDocument](SchemaMigrator.versionDocId)
        .map(_.responseBody.docs.head)
      _ = assertEquals(doc.currentSchemaVersion, migrations.map(_.version).max)
    yield ()

  test("convert previous version document to current, no remaining migrations"):
    for
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      _ <- truncate(client)
      _ <- client.modifySchema(
        Seq(Add(Field(FieldName("currentSchemaVersion"), TypeName("plong"))))
      )

      oldDoc = VersionDocument.HistoricDocument1(
        SchemaMigrator.versionDocId,
        migrations.map(_.version).maxOption,
        DocVersion.NotExists
      )
      _ <- client.upsertSuccess(Seq(oldDoc))

      res <- migrator.migrate(migrations)
      _ = assertEquals(res.migrationsRun, 0L)
      doc <- client
        .findById[VersionDocument](SchemaMigrator.versionDocId)
        .map(_.responseBody.docs.head)
      _ = assertEquals(doc.currentSchemaVersion, migrations.map(_.version).max)
    yield ()

  test("no-op when new version document exists"):
    for
      client <- IO(solrClient())
      migrator = SchemaMigrator(client)
      _ <- truncate(client)

      doc = VersionDocument(
        SchemaMigrator.versionDocId,
        migrations.map(_.version).max,
        false,
        DocVersion.NotExists
      )
      _ <- client.upsertSuccess(Seq(doc))

      res <- migrator.migrate(migrations)
      _ = assertEquals(res.migrationsRun, 0L)
    yield ()
