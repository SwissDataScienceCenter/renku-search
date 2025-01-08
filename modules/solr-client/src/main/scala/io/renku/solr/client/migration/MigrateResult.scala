package io.renku.solr.client.migration

final case class MigrateResult(
    startVersion: Option[Long],
    endVersion: Option[Long],
    migrationsRun: Long,
    migrationsSkipped: Long,
    reindexRequired: Boolean
)

object MigrateResult:
  val empty: MigrateResult =
    MigrateResult(None, None, 0L, 0L, false)
