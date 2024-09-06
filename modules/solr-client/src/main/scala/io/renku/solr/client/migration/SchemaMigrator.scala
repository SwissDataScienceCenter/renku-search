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

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import fs2.io.net.Network

import io.renku.solr.client.*
import io.renku.solr.client.util.DocumentLockResource

trait SchemaMigrator[F[_]] {

  def currentVersion: F[Option[Long]]

  def migrate(migrations: Seq[SchemaMigration]): F[MigrateResult]
}

object SchemaMigrator:
  private[migration] val versionDocId = "VERSION_ID_EB779C6B-1D96-47CB-B304-BECF15E4A607"

  def apply[F[_]: Sync](client: SolrClient[F]): SchemaMigrator[F] = Impl[F](client)

  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SchemaMigrator[F]] =
    SolrClient[F](solrConfig).map(apply[F])

  private class Impl[F[_]: Sync](client: SolrClient[F]) extends SchemaMigrator[F] {
    private val logger = scribe.cats.effect[F]
    private val migrateLock = DocumentLockResource[F, VersionDocument](client)

    override def currentVersion: F[Option[Long]] =
      getVersionDoc.map(_.map(_.currentSchemaVersion))

    private def getVersionDoc: F[Option[VersionDocument]] =
      client
        .findById[VersionDocument](versionDocId)
        .map(_.responseBody.docs.headOption)

    def migrate(migrations: Seq[SchemaMigration]): F[MigrateResult] =
      convertVersionDocument >>
        migrateLock.make(versionDocId).use {
          case None =>
            logger.info("A migration is already running").as(MigrateResult.empty)
          case Some(doc) => doMigrate(migrations, doc)
        }

    def doMigrate(
        migrations: Seq[SchemaMigration],
        initial: VersionDocument
    ): F[MigrateResult] = for {
      _ <- logger.info(
        s"core ${client.config.core}: Found current schema version '${initial.currentSchemaVersion}' using id $versionDocId"
      )
      remain = migrations
        .sortBy(_.version)
        .dropWhile(m => m.version <= initial.currentSchemaVersion)
      _ <- logger.info(
        s"core ${client.config.core}: There are ${remain.size} migrations to run"
      )

      finalDoc <- remain.foldLeftM(initial) { (doc, m) =>
        client.modifySchema(m.commands) >> upsertVersion(doc, m.version)
      }

      result = MigrateResult(
        startVersion = Option(initial.currentSchemaVersion).filter(_ > Long.MinValue),
        endVersion = remain.map(_.version).maxOption,
        migrationsRun = remain.size,
        reindexRequired = remain.exists(_.requiresReIndex)
      )
    } yield result

    private def requireVersionDoc =
      getVersionDoc.flatMap {
        case None =>
          Sync[F].raiseError(
            new Exception("No version document available during migration!")
          )
        case Some(d) => d.pure[F]
      }

    private def upsertVersion(currentDoc: VersionDocument, nextVersion: Long) =
      for
        _ <- logger.info(
          s"core ${client.config.core}: Set schema migration version to $nextVersion"
        )
        _ <- client.upsertSuccess(
          Seq(currentDoc.copy(currentSchemaVersion = nextVersion))
        )
        doc <- requireVersionDoc
      yield doc

    private def convertVersionDocument =
      val task = client.lockOn("fc72c840-67a8-4a42-8ce1-f9baa409ea84").use {
        case true =>
          client
            .findById[VersionDocument.HistoricDocument1](versionDocId)
            .map(_.responseBody.docs.headOption)
            .map(_.flatMap(_.toCurrent))
            .flatMap {
              case None => ().pure[F]
              case Some(d) =>
                logger.info(s"Converting old version document $d") >> client
                  .upsertSuccess(Seq(d))
            }
            .as(true)
        case false => false.pure[F]
      }
      fs2.Stream.repeatEval(task).takeWhile(!_).compile.drain
  }
