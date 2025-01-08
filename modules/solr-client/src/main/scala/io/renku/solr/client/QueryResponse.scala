package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.facet.FacetResponse

final case class QueryResponse[A](
    responseHeader: ResponseHeader,
    @key("response") responseBody: ResponseBody[A],
    @key("facets") facetResponse: Option[FacetResponse] = None,
    @key("nextCursorMark") nextCursor: Option[CursorMark] = None
):
  def map[B](f: A => B): QueryResponse[B] =
    copy(responseBody = responseBody.map(f))

object QueryResponse:
  given [A](using Decoder[A]): Decoder[QueryResponse[A]] =
    MapBasedCodecs.deriveDecoder
