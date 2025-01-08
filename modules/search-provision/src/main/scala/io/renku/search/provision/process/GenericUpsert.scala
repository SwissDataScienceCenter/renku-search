package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*
import fs2.Pipe

import io.renku.search.events.EventMessage
import io.renku.search.provision.handler.*
import io.renku.solr.client.UpsertResponse
import io.renku.solr.client.UpsertResponse.syntax.*

/** A generic insert or update.
  *
  * It uses the `IdExtractor` and `DocumentMerger` for any payload type `A` to do the
  * following basic steps:
  *
  *   - Using `IdExtractor`, get the ID of each payload entity
  *   - Attempt to fetch a document from the index using this ID. The result may be None
  *     or a document that is either "partial" or "full"
  *   - Using `DocumentMerger`, merge the data from the payload with the fetched document
  *     (if available) or insert a new document generated from the payload
  *   - The resulting document is pushed to solr, it will replace any existing document
  *     with the same ID
  *   - If a conflict occurs, because a parallel request modified the document
  *     concurrently, the operation is retried (from step 1)
  *
  * This pattern works for some messages, other may require more logic and therefore a
  * separate implementation.
  */
final private[provision] class GenericUpsert[F[_]: Async](ps: PipelineSteps[F]):

  /** Upsert all documents in the event payload */
  def process1[A](
      msg: EventMessage[A]
  )(using DocumentMerger[A], IdExtractor[A]): F[UpsertResponse] =
    for
      loaded <- ps.fetchFromSolr.loadEntityOrPartial(msg)
      res <- processLoaded1(loaded)
    yield res

  def processLoaded1[A](
      msg: EntityOrPartialMessage[A]
  )(using DocumentMerger[A], IdExtractor[A]): F[UpsertResponse] =
    val merger = DocumentMerger[A]
    val updated = msg.merge(merger.create, merger.merge)
    ps.pushToSolr.pushAll(updated)

  /** Same as `process1` but retries on version conflicts. */
  def process[A](msg: EventMessage[A], retries: Int)(using
      DocumentMerger[A],
      IdExtractor[A]
  ): F[UpsertResponse] =
    process1(msg).retryOnConflict(retries)

  def processPipe[A](retries: Int)(using
      DocumentMerger[A],
      IdExtractor[A]
  ): Pipe[F, EventMessage[A], UpsertResponse] =
    _.evalMap(process(_, retries))
