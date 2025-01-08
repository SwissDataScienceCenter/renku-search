package io.renku.search.http.borer

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{Borer, Encoder}
import org.http4s._

final case class BorerDecodeFailure(respString: String, error: Borer.Error[?])
    extends DecodeFailure {

  override val message: String = s"${error.getMessage}: $respString"

  override val cause: Option[Throwable] = Option(error.getCause)

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(status = Status.BadRequest).withEntity(this)
}

object BorerDecodeFailure:
  given Encoder[Borer.Error[?]] = Encoder.forString.contramap(_.getMessage)
  given Encoder[BorerDecodeFailure] = deriveEncoder
  given [F[_]]: EntityEncoder[F, BorerDecodeFailure] =
    BorerEntities.encodeEntityJson[F, BorerDecodeFailure]
