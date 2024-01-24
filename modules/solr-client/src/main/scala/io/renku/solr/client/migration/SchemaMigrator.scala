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

import cats.effect.Sync
import cats.syntax.all.*
import io.renku.solr.client.schema.{Field, FieldName, SchemaCommand, TypeName}
import io.renku.solr.client.{QueryString, SolrClient}

trait SchemaMigrator[F[_]] {

  def currentVersion: F[Option[Long]]

  def migrate(migrations: Seq[SchemaMigration]): F[Unit]
}

object SchemaMigrator:
  def apply[F[_]: Sync](client: SolrClient[F]): SchemaMigrator[F] = Impl[F](client)

  private class Impl[F[_]: Sync](client: SolrClient[F]) extends SchemaMigrator[F] {
    private[this] val logger = scribe.cats.effect[F]
    private[this] val versionDocId = "VERSION_ID_EB779C6B-1D96-47CB-B304-BECF15E4A607"
    private[this] val versionTypeName: TypeName = TypeName("plong")

    override def currentVersion: F[Option[Long]] =
      client
        .query[VersionDocument](VersionDocument.schema, QueryString(s"id:$versionDocId"))
        .map(_.responseBody.docs.headOption.map(_.currentSchemaVersion))

    override def migrate(migrations: Seq[SchemaMigration]): F[Unit] = for {
      current <- currentVersion
      _ <- current.fold(initVersionDocument)(_ => ().pure[F])
      remain = migrations.sortBy(_.version).dropWhile(m => current.exists(_ >= m.version))
      _ <- remain.traverse_(m =>
        client.modifySchema(m.commands) >> upsertVersion(m.version)
      )
    } yield ()

    private def initVersionDocument: F[Unit] =
      logger.info("Initialize schema migration version document") >>
        client.modifySchema(
          Seq(
            // SchemaCommand.Add(FieldType.long(versionTypeName)),
            SchemaCommand.Add(
              Field(
                FieldName("currentSchemaVersion"),
                versionTypeName,
                required = true
              )
            )
          )
        )

    private def version(n: Long): VersionDocument = VersionDocument(versionDocId, n)

    private def upsertVersion(n: Long) =
      logger.info(s"Set schema migration version to $n") >>
        client.insert(VersionDocument.schema, Seq(version(n)))
  }