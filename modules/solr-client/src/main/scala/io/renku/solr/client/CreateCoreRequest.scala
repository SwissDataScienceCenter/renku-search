package io.renku.solr.client

import io.bullet.borer.Encoder
import io.bullet.borer.Writer
import io.bullet.borer.derivation.MapBasedCodecs

final case class CreateCoreRequest(
    name: String,
    configSet: String
)

object CreateCoreRequest:

  given Encoder[CreateCoreRequest] =
    given inner: Encoder[CreateCoreRequest] =
      MapBasedCodecs.deriveEncoder[CreateCoreRequest]
    new Encoder[CreateCoreRequest] {
      def write(w: Writer, v: CreateCoreRequest): Writer =
        w.writeMapOpen(1)
        w.writeMapMember("create", v)
        w.writeMapClose()
    }
