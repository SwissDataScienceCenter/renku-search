package io.renku.solr.client

import cats.data.NonEmptyList

import io.bullet.borer.{Encoder, Writer}

final private[client] case class DeleteIdRequest(ids: NonEmptyList[String])

private[client] object DeleteIdRequest:
  given Encoder[DeleteIdRequest] =
    new Encoder[DeleteIdRequest]:
      override def write(w: Writer, value: DeleteIdRequest) =
        w.writeMap(Map("delete" -> value.ids.toList))(using
          Encoder[String],
          Encoder[List[String]]
        )
