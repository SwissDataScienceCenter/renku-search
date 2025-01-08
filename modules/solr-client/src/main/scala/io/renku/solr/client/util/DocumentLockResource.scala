package io.renku.solr.client.util

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.*

/** Utilising SOLRs optimistic locking, create a [[cats.effect.Resource]] implementing a
  * lock.
  *
  * NOTE: The document represented as `A` must supply the necessary properties to enable
  * SOLRs optimistic locking, it must have at least an `id` and `_version_` property!
  */
final class DocumentLockResource[F[_]: MonadThrow, A](
    client: SolrClient[F]
)(using e: LockDocument[F, A]):
  private given Encoder[A] = e.encoder
  private given Decoder[A] = e.decoder

  /** Creates a resource that is represented by a solr document with the given
    * `documentId`. The resource is considered acquired, if the document either can be
    * inserted (it doesn't exist) or passes the `isFree` check if it exists. If this is
    * fulfilled, the `acquire` function must return the "acquired" state of the document
    * that will be upserted to SOLR (this state must not pass the `isFree` check).
    * Finally, the `release` function determines the next free state of the resource. If
    * `release` returns `None` the document will be deleted from SOLR.
    *
    * The resource yields a `Some` if the lock was acquired successfully. It yields a
    * `None` if the document was in "non-free" state and therefore was not acquired
    * successfully.
    */
  def make(documentId: String): Resource[F, Option[A]] = {
    val acq = for
      existing <- getDocument(documentId)
      res <-
        if (existing.forall(e.isFree))
          e.acquire(existing, documentId).map(Seq(_)).flatMap(client.upsert(_)).flatMap {
            case UpsertResponse.VersionConflict => None.pure[F]
            case UpsertResponse.Success(_)      => getDocument(documentId)
          }
        else None.pure[F]
    yield res

    def rel(a: Option[A]) = a match {
      case None => ().pure[F]
      case Some(_) =>
        requireDocument(documentId)
          .map(e.release)
          .flatMap {
            case Some(d) => client.upsertSuccess(Seq(d))
            case None    => client.deleteIds(NonEmptyList.of(documentId))
          }
    }
    Resource.make(acq)(rel)
  }

  private def getDocument(docId: String): F[Option[A]] =
    client
      .query[A](QueryString(s"id:$docId"))
      .map(_.responseBody.docs.headOption)

  private def requireDocument(docId: String) =
    getDocument(docId).flatMap {
      case None =>
        MonadThrow[F].raiseError(
          new Exception("No document available during resource release!")
        )
      case Some(d) => d.pure[F]
    }

object DocumentLockResource:

  def apply[F[_]: MonadThrow, A](
      client: SolrClient[F]
  )(using LockDocument[F, A]): DocumentLockResource[F, A] =
    new DocumentLockResource[F, A](client)

  final private case class SimpleLock(
      id: String,
      @key("_version_") version: DocVersion
  )
  private object SimpleLock:
    given Encoder[SimpleLock] = MapBasedCodecs.deriveEncoder
    given Decoder[SimpleLock] = MapBasedCodecs.deriveDecoder
    given [F[_]: MonadThrow]: LockDocument[F, SimpleLock] =
      LockDocument.whenExists(id =>
        MonadThrow[F].pure(SimpleLock(id, DocVersion.NotExists))
      )

  def create[F[_]: MonadThrow](client: SolrClient[F])(id: String): Resource[F, Boolean] =
    DocumentLockResource[F, SimpleLock](client)
      .make(id)
      .map(_.isDefined)
