package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.derivation.key

final case class GetByIdResponse[A](
    @key("response") responseBody: ResponseBody[A]
):
  def map[B](f: A => B): GetByIdResponse[B] =
    copy(responseBody = responseBody.map(f))

object GetByIdResponse:
  given [A](using Decoder[A]): Decoder[GetByIdResponse[A]] =
    deriveDecoder
