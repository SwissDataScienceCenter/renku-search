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
import io.renku.solr.client.util.{SolrSpec, SolrTruncate}
import munit.CatsEffectSuite

class SolrMigratorSpec extends CatsEffectSuite with SolrSpec with SolrTruncate:
  val logger = scribe.cats.io
  val migrations = Seq(
    SchemaMigration(1, Add(FieldType.text(TypeName("text"), Analyzer.classic))),
    SchemaMigration(2, Add(FieldType.int(TypeName("int")))),
    SchemaMigration(3, Add(Field(FieldName("name"), TypeName("text")))),
    SchemaMigration(4, Add(Field(FieldName("description"), TypeName("text")))),
    SchemaMigration(5, Add(Field(FieldName("seats"), TypeName("int"))))
  )

  def truncate(client: SolrClient[IO]): IO[Unit] =
    truncateAll(client)(
      Seq(
        FieldName("currentSchemaVersion"),
        FieldName("name"),
        FieldName("description"),
        FieldName("seats")
      ),
      Seq(TypeName("text"), TypeName("int"))
    )

  test("run sample migrations"):
    withSolrClient().use { client =>
      val migrator = SchemaMigrator[IO](client)
      for {
        _ <- truncate(client)
        _ <- migrator.migrate(migrations)
        c <- migrator.currentVersion
        _ = assertEquals(c, Some(5L))
      } yield ()
    }

  test("run only remaining migrations"):
    withSolrClient().use { client =>
      val migrator = SchemaMigrator(client)
      val first = migrations.take(2)
      for {
        _ <- truncate(client)
        _ <- migrator.migrate(first)
        v0 <- migrator.currentVersion
        _ = assertEquals(v0, Some(2L))

        _ <- migrator.migrate(migrations)
        v1 <- migrator.currentVersion
        _ = assertEquals(v1, Some(5L))
      } yield ()
    }
