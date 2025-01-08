package io.renku.search.model

import io.bullet.borer.Codec

opaque type Description = String
object Description:
  def apply(v: String): Description = v
  def from(v: Option[String]): Option[Description] =
    v.flatMap {
      _.trim match {
        case "" => Option.empty[Description]
        case o  => Option(o)
      }
    }
  extension (self: Description) def value: String = self
  given Codec[Description] = Codec.of[String]
