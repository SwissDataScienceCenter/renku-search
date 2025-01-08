package io.renku.solr.client

import scala.concurrent.duration.*

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all.*

import io.bullet.borer.Json
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.http.{HttpClientDsl, ResponseLogging}
import io.renku.solr.client.schema.{SchemaCommand, SchemaJsonCodec}
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.{BasicCredentials, Method, Uri}

private class SolrClientImpl[F[_]: Async](val config: SolrConfig, underlying: Client[F])
    extends SolrClient[F]
    with HttpClientDsl[F]
    with SchemaJsonCodec
    with BorerEntityJsonCodec
    with SolrEntityCodec:
  private val logger = scribe.cats.effect[F]
  private val solrUrl: Uri = config.baseUrl / "solr" / config.core

  def modifySchema(cmds: Seq[SchemaCommand], onErrorLog: ResponseLogging): F[Unit] =
    val req = Method
      .POST(cmds, (solrUrl / "schema").withQueryParam("commit", "true"))
      .withBasicAuth(credentials)
    underlying.expectOr[String](req)(onErrorLog(logger, req)).void

  def query[A: Decoder](q: QueryString): F[QueryResponse[A]] =
    query[A](QueryData(q))

  def query[A: Decoder](query: QueryData): F[QueryResponse[A]] =
    val req = Method.POST(query, solrUrl / "query").withBasicAuth(credentials)
    logger.debug(s"SOLR Query: ${Json.encode(query).toUtf8String}") >>
      underlying
        .expectOr[QueryResponse[A]](req)(ResponseLogging.Error(logger, req))
        .flatTap(r => logger.trace(s"Query response: $r"))

  def delete(q: QueryString): F[Unit] =
    val req = Method.POST(DeleteRequest(q.q), makeUpdateUrl).withBasicAuth(credentials)
    underlying
      .expectOr[InsertResponse](req)(ResponseLogging.Error(logger, req))
      .flatTap(r => logger.trace(s"Solr delete response: $r"))
      .void

  def deleteIds(ids: NonEmptyList[String]): F[Unit] =
    val req = Method
      .POST(DeleteIdRequest(ids), makeUpdateUrl)
      .withBasicAuth(credentials)
    underlying
      .expectOr[InsertResponse](req)(ResponseLogging.Error(logger, req))
      .flatTap(r => logger.trace(s"Solr delete response: $r"))
      .void

  def upsert[A: Encoder](docs: Seq[A]): F[UpsertResponse] =
    val req = Method
      .POST(docs, makeUpdateUrl)
      .withBasicAuth(credentials)
    underlying.run(req).evalTap(r => logger.trace(s"Solr inserted response: $r")).use {
      resp =>
        resp.status match
          case Status.Ok =>
            resp.as[InsertResponse].map(r => UpsertResponse.Success(r.responseHeader))
          case Status.Conflict =>
            UpsertResponse.VersionConflict.pure[F]
          case _ =>
            ResponseLogging
              .Error(logger, req)
              .apply(resp)
              .flatMap(ex => Async[F].raiseError(ex))
    }

  def upsertSuccess[A: Encoder](docs: Seq[A]): F[Unit] =
    upsert[A](docs).flatMap {
      case UpsertResponse.Success(_) => ().pure[F]
      case UpsertResponse.VersionConflict =>
        Async[F].raiseError(
          new Exception(s"Inserting $docs failed due to version conflict")
        )
    }

  def upsertLoop[D: Decoder: Encoder, R](
      id: String,
      timeout: FiniteDuration,
      interval: FiniteDuration
  )(
      update: Option[D] => (Option[D], R)
  ): F[R] =
    val task =
      findById[D](id).map(_.responseBody.docs.headOption).map(update).flatMap {
        case (None, r)      => (UpsertResponse.Success(ResponseHeader.empty), r).pure[F]
        case (Some(doc), r) => upsert(Seq(doc)).map(_ -> r)
      }
    fs2.Stream
      .repeatEval(task)
      .meteredStartImmediately(interval)
      .takeThrough(_._1.isFailure)
      .map(_._2)
      .timeout(timeout)
      .compile
      .lastOrError

  def getSchema: F[SchemaResponse] =
    val url = solrUrl / "schema"
    val req = Method.GET(url).withBasicAuth(credentials)
    underlying.expect[SchemaResponse](req)

  def getStatus: F[StatusResponse] =
    val url = config.baseUrl / "api" / "cores"
    val req = Method.GET(url).withBasicAuth(credentials)
    underlying.expect[StatusResponse](req)

  def createCore(name: String, configSet: Option[String]): F[Unit] =
    val url = config.baseUrl / "api" / "cores"
    val req = Method
      .POST(CreateCoreRequest(name, configSet.getOrElse("_default")), url)
      .withBasicAuth(credentials)
    underlying.fetchAs[CoreResponse](req).flatMap { resp =>
      resp.error.map(_.message) match
        case Some(msg) =>
          Async[F].raiseError(new Exception(s"Creating core '$name' failed: $msg"))
        case None => ().pure[F]
    }

  def deleteCore(name: String): F[Unit] =
    val url = config.baseUrl / "api" / "cores" / name
    val req = Method
      .POST(DeleteCoreRequest(true, true), url)
      .withBasicAuth(credentials)
    underlying.fetchAs[CoreResponse](req).flatMap { resp =>
      resp.error.map(_.message) match
        case Some(msg) =>
          Async[F].raiseError(new Exception(s"Deleting core '$name' failed: $msg"))
        case None => ().pure[F]
    }

  private def makeUpdateUrl =
    (solrUrl / "update")
      .withQueryParam("overwrite", "true")
      .withQueryParam("wt", "json")
      .withQueryParam("commit", "true")

  override def findById[A: Decoder](id: String, other: String*): F[GetByIdResponse[A]] =
    val req = Method
      .GET(makeGetByIdUrl(NonEmptyList.of(id, other*)))
      .withBasicAuth(credentials)
    underlying.fetchAs[GetByIdResponse[A]](req)

  private def makeGetByIdUrl(ids: NonEmptyList[String]) =
    (solrUrl / "get").withQueryParam("ids", ids.toList)

  private lazy val credentials: Option[BasicCredentials] =
    config.maybeUser.map(u => BasicCredentials(u.username, u.password.value))
