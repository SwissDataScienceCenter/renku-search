package io.renku.solr.client.facet

import io.bullet.borer.Decoder
import io.bullet.borer.Reader
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.facet.FacetResponse.Values
import io.renku.solr.client.schema.FieldName

final case class FacetResponse(
    count: Int,
    buckets: Map[FieldName, Values]
):
  def isEmpty: Boolean = count == 0 && buckets.isEmpty

  private def withBuckets(field: FieldName, values: Values): FacetResponse =
    copy(buckets = buckets.updated(field, values))

object FacetResponse:
  val empty: FacetResponse = FacetResponse(0, Map.empty)

  final case class Bucket(@key("val") value: String, count: Int)
  object Bucket:
    given Decoder[Bucket] = MapBasedCodecs.deriveDecoder

  final case class Values(buckets: Buckets)
  object Values:
    given Decoder[Values] = MapBasedCodecs.deriveDecoder

  type Buckets = Seq[Bucket]

  given Decoder[FacetResponse] = new Decoder[FacetResponse] {
    def read(r: Reader): FacetResponse =
      r.readMapStart()
      r.readUntilBreak(empty) { fr =>
        val nextKey = r.readString()
        if (nextKey == "count") fr.copy(count = r.readInt())
        else fr.withBuckets(FieldName(nextKey), r.read[Values]())
      }
  }
