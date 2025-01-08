package io.renku.solr.client

import cats.effect.Concurrent

import org.http4s.EntityDecoder

trait SolrEntityCodec {
  given [F[_]: Concurrent]: EntityDecoder[F, String] =
    EntityDecoder.text
}

object SolrEntityCodec extends SolrEntityCodec
