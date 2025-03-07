package io.renku.solr.client.util

import io.bullet.borer.*

/** Documents implementing this interface can be used for locking with
  * `DocumentLockResource`.
  *
  * These documents must also provide at least an `id` and `_version_` property to enable
  * SOLRs optimistic locking.
  */
trait LockDocument[F[_], A]:
  def encoder: Encoder[A]
  def decoder: Decoder[A]

  /** Determines if an existing document is in "free" state. */
  def isFree(value: A): Boolean

  /** Return a document in "acquired" state, either set it given an existing document or
    * create a new document in this state using the provided `id`.
    */
  def acquire(existing: Option[A], id: String): F[A]

  /** Given the document in "acquired" state, return it in "free" state. Return `None` to
    * have it deleted from the index.
    */
  def release(value: A): Option[A]

object LockDocument:

  def apply[F[_], A: Encoder: Decoder](
      _isFree: A => Boolean,
      _acquire: (Option[A], String) => F[A],
      _release: A => Option[A]
  ): LockDocument[F, A] = new LockDocument[F, A] {
    val encoder: Encoder[A] = summon[Encoder[A]]
    val decoder: Decoder[A] = summon[Decoder[A]]
    def isFree(value: A): Boolean = _isFree(value)
    def acquire(existing: Option[A], id: String): F[A] = _acquire(existing, id)
    def release(value: A): Option[A] = _release(value)
  }

  /** A version that is based only on existence of a document.
    *
    * If the document exists in SOLR, the resource yields a `None` (not acquired). If the
    * document doesn't exist, it is inserted and the current version returned as a `Some`.
    */
  def whenExists[F[_], A: Encoder: Decoder](create: String => F[A]): LockDocument[F, A] =
    apply(_isFree = _ => false, _acquire = (_, id) => create(id), _release = _ => None)
