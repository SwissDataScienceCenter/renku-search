package io.renku.solr.client.facet

import io.bullet.borer.Encoder

enum FacetAlgorithm:
  case DocValues
  case UnInvertedField
  case DocValuesHash
  case Enum
  case Stream
  case Smart

  private[client] def name: String = this match
    case DocValues       => "dv"
    case UnInvertedField => "uif"
    case DocValuesHash   => "dvhash"
    case Enum            => "enum"
    case Stream          => "stream"
    case Smart           => "smart"

object FacetAlgorithm:
  given Encoder[FacetAlgorithm] = Encoder.forString.contramap(_.name)
