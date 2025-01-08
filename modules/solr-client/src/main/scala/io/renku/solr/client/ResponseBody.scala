package io.renku.solr.client

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.{Decoder, Encoder}

final case class ResponseBody[A](
    numFound: Long,
    start: Long,
    numFoundExact: Boolean,
    docs: Seq[A]
):
  def map[B](f: A => B): ResponseBody[B] =
    copy(docs = docs.map(f))

object ResponseBody:
  given [A](using Decoder[A]): Decoder[ResponseBody[A]] = MapBasedCodecs.deriveDecoder
  given [A](using Encoder[A]): Encoder[ResponseBody[A]] = MapBasedCodecs.deriveEncoder

  def single[A](value: A): ResponseBody[A] =
    ResponseBody(1, 0, true, Seq(value))
