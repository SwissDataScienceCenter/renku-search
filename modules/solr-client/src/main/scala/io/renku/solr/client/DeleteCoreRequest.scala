package io.renku.solr.client

import io.bullet.borer.Encoder
import io.bullet.borer.Writer
import io.bullet.borer.derivation.MapBasedCodecs

final case class DeleteCoreRequest(
    deleteInstanceDir: Boolean,
    deleteIndex: Boolean
)

object DeleteCoreRequest:

  given Encoder[DeleteCoreRequest] =
    given inner: Encoder[DeleteCoreRequest] =
      MapBasedCodecs.deriveEncoder[DeleteCoreRequest]
    new Encoder[DeleteCoreRequest] {
      def write(w: Writer, v: DeleteCoreRequest): Writer =
        w.writeMapOpen(1)
        w.writeMapMember("unload", v)
        w.writeMapClose()
    }
