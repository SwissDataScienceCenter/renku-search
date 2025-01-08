package io.renku.solr.client.facet

import io.bullet.borer.Encoder
import io.bullet.borer.Writer
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key

final case class FacetRange(
    from: FacetRange.Value,
    to: FacetRange.Value,
    @key("inclusive_from") inclusiveFrom: Boolean = true,
    @key("inclusive_to") inclusiveTo: Boolean = false
)
object FacetRange:
  case object All
  type Value = Int | All.type

  given Encoder[Value] = new Encoder[Value]:
    override def write(w: Writer, v: Value) = v match
      case n: Int => w.write(n)
      case All    => w.write("*")

  given Encoder[FacetRange] = MapBasedCodecs.deriveEncoder
