package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.{MapBasedCodecs, key}

final case class CoreResponse(
    responseHeader: ResponseHeader,
    error: Option[CoreResponse.Error] = None,
    core: Option[String] = None
):

  def isSuccess: Boolean = error.isEmpty

object CoreResponse:

  final case class Error(@key("msg") message: String)
  object Error:
    given Decoder[Error] = MapBasedCodecs.deriveDecoder

  given Decoder[CoreResponse] = MapBasedCodecs.deriveDecoder
