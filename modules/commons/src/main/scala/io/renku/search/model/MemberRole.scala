package io.renku.search.model

import cats.kernel.Order

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{Decoder, Encoder}

enum MemberRole:
  lazy val name: String = productPrefix.toLowerCase
  case Owner, Editor, Viewer, Member

object MemberRole:

  val valuesV1: Set[MemberRole] = Set(Owner, Member)
  val valuesV2: Set[MemberRole] = Set(Owner, Editor, Viewer)

  val valuesLowerFirst: List[MemberRole] =
    values.toList.sortBy(-_.ordinal)

  given Order[MemberRole] = Order.by(_.ordinal)

  given Encoder[MemberRole] = Encoder.forString.contramap(_.name)

  given Decoder[MemberRole] = Decoder.forString.mapEither(MemberRole.fromString)

  def fromString(v: String): Either[String, MemberRole] =
    MemberRole.values
      .find(_.name.equalsIgnoreCase(v))
      .toRight(s"Invalid member-role: $v")

  def unsafeFromString(v: String): MemberRole =
    fromString(v).fold(sys.error, identity)
