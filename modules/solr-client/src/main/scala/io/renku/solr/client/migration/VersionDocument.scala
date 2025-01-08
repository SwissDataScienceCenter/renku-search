package io.renku.solr.client.migration

import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder}
import io.renku.solr.client.DocVersion
import io.renku.solr.client.util.LockDocument

final private[client] case class VersionDocument(
    id: String,
    @key("current_schema_version_l") currentSchemaVersion: Long,
    @key("migration_running_b") migrationRunning: Boolean,
    @key("_version_") version: DocVersion
)

private[client] object VersionDocument:
  given Encoder[VersionDocument] = MapBasedCodecs.deriveEncoder
  given Decoder[VersionDocument] = MapBasedCodecs.deriveDecoder

  given [F[_]: Sync]: LockDocument[F, VersionDocument] =
    LockDocument(!_.migrationRunning, acquired, _.copy(migrationRunning = false).some)

  private def acquired[F[_]: Sync](existing: Option[VersionDocument], id: String) =
    Sync[F].pure(
      existing
        .map(_.copy(migrationRunning = true))
        .getOrElse(VersionDocument(id, Long.MinValue, true, DocVersion.NotExists))
    )

  final private[client] case class HistoricDocument1(
      id: String,
      currentSchemaVersion: Option[Long] = None,
      @key("_version_") version: DocVersion
  ):
    def toCurrent: Option[VersionDocument] =
      currentSchemaVersion.map(VersionDocument(id, _, false, version))

  private[client] object HistoricDocument1:
    given Encoder[HistoricDocument1] = MapBasedCodecs.deriveEncoder
    given Decoder[HistoricDocument1] = MapBasedCodecs.deriveDecoder
