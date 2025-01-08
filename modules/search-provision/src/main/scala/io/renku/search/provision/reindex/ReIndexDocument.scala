package io.renku.search.provision.reindex

import java.time.Instant

import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.*
import io.bullet.borer.NullOptions.*
import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.renku.json.codecs.all.given
import io.renku.search.events.MessageId
import io.renku.search.model.Id
import io.renku.solr.client.DocVersion
import io.renku.solr.client.util.LockDocument

final private case class ReIndexDocument(
    id: Id,
    @key("created_dt") created: Instant,
    @key("message_id_s") messageId: Option[MessageId] = None,
    @key("_version_") version: DocVersion
)

private object ReIndexDocument:
  given Encoder[ReIndexDocument] = MapBasedCodecs.deriveEncoder
  given Decoder[ReIndexDocument] = MapBasedCodecs.deriveDecoder

  def lockDocument[F[_]: Sync: Clock](
      messageId: Option[MessageId]
  ): LockDocument[F, ReIndexDocument] =
    LockDocument.whenExists(id =>
      Clock[F].realTimeInstant.map { now =>
        ReIndexDocument(Id(id), now, messageId, DocVersion.NotExists)
      }
    )
