package io.renku.solr.client

import io.bullet.borer.derivation.MapBasedCodecs.deriveEncoder
import io.bullet.borer.{Encoder, Writer}

final private[client] case class DeleteRequest(query: String)

private[client] object DeleteRequest:
  given Encoder[DeleteRequest] = {
    val e: Encoder[DeleteRequest] = deriveEncoder[DeleteRequest]
    new Encoder[DeleteRequest]:
      override def write(w: Writer, value: DeleteRequest) =
        w.writeMap(Map("delete" -> value))(using Encoder[String], e)
  }
