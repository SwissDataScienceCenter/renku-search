package io.renku.search.provision.process

import cats.effect.*

import io.renku.search.events.EventMessage
import io.renku.search.provision.handler.*

/** A generic delete.
  *
  * It uses the `IdExtractor` to obtain an ID for each payload entity and uses it to issue
  * a DELETE request at SOLR.
  */
final private[provision] class GenericDelete[F[_]: Async](ps: PipelineSteps[F]):

  /** Delete all documents in the payload. */
  def process[A](msg: EventMessage[A])(using
      IdExtractor[A]
  ): F[DeleteFromSolr.DeleteResult[A]] =
    ps.deleteFromSolr.deleteDocuments(msg)
