package io.renku.solr.client

import scala.concurrent.duration.*

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import fs2.io.net.Network

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.http.{ClientBuilder, ResponseLogging, RetryConfig}
import io.renku.solr.client.schema.SchemaCommand
import io.renku.solr.client.util.{DocumentLockResource, LockDocument}
import org.http4s.ember.client.EmberClientBuilder

trait SolrClient[F[_]]:
  def config: SolrConfig
  def modifySchema(
      cmds: Seq[SchemaCommand],
      onErrorLog: ResponseLogging = ResponseLogging.Error
  ): F[Unit]

  def query[A: Decoder](q: QueryString): F[QueryResponse[A]]

  def query[A: Decoder](q: QueryData): F[QueryResponse[A]]

  def delete(q: QueryString): F[Unit]
  def deleteIds(ids: NonEmptyList[String]): F[Unit]

  def upsert[A: Encoder](docs: Seq[A]): F[UpsertResponse]
  def upsertSuccess[A: Encoder](docs: Seq[A]): F[Unit]
  def upsertLoop[D: Decoder: Encoder, R](
      id: String,
      timeout: FiniteDuration = 1.seconds,
      interval: FiniteDuration = 15.millis
  )(
      update: Option[D] => (Option[D], R)
  ): F[R]

  def findById[A: Decoder](id: String, other: String*): F[GetByIdResponse[A]]

  def getSchema: F[SchemaResponse]

  def getStatus: F[StatusResponse]
  def createCore(name: String, configSet: Option[String] = None): F[Unit]
  def deleteCore(name: String): F[Unit]

  /** Returns a `Resource` that yields `true` if a lock for `id` could be obtained. It
    * yields `false` if the lock `id` is already held.
    *
    * It uses a solr document of the given `id`.
    */
  def lockOn(id: String)(using MonadThrow[F]): Resource[F, Boolean] =
    DocumentLockResource.create[F](this)(id)

  /** Returns a `Resource` that yields a `Some` if the lock represented by `A` could be
    * obtained and `None` if not.
    *
    * The lock is represented by a solr document `A`. The `acquire` function either
    * returns a new document in "acquired" state or sets the acquired state should the
    * document already exist. Analogous, `release` puts the document back into free state
    * or return `None` to remove the document from SOLR. The function `isFree` is used to
    * determine the state if a document already exists with that id. If it doesn't exist,
    * the lock is free to obtain.
    */
  def lockBy[A](
      id: String
  )(using MonadThrow[F], LockDocument[F, A]): Resource[F, Option[A]] =
    DocumentLockResource[F, A](this).make(id)

object SolrClient:
  def apply[F[_]: Async: Network](config: SolrConfig): Resource[F, SolrClient[F]] =
    ClientBuilder(EmberClientBuilder.default[F])
      .withDefaultRetry(RetryConfig.default)
      .withLogging(logBody = config.logMessageBodies, scribe.cats.effect[F])
      .build
      .map(new SolrClientImpl[F](config, _))
