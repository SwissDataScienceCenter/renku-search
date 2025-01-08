package io.renku.search.solr.documents

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.renku.json.EncoderSupport
import io.renku.search.solr.schema.EntityDocumentSchema.Fields

enum DocumentKind:
  case FullEntity
  case PartialEntity

  val name: String = productPrefix.toLowerCase

  private[documents] def additionalField[A]: EncoderSupport.AdditionalFields[A, String] =
    EncoderSupport.AdditionalFields.const[A, String](Fields.kind.name -> name)

object DocumentKind:
  given Encoder[DocumentKind] = Encoder.forString.contramap(_.name)
  given Decoder[DocumentKind] = Decoder.forString.mapEither(fromString)

  def fromString(s: String): Either[String, DocumentKind] =
    DocumentKind.values
      .find(_.name.equalsIgnoreCase(s))
      .toRight(s"Invalid document kind: $s")

  def unsafeFromString(s: String): DocumentKind =
    fromString(s).fold(sys.error, identity)
