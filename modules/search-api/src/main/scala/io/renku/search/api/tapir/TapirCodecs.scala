package io.renku.search.api.tapir

import io.renku.search.api.data.*
import io.renku.search.model.{EntityType, Id}
import io.renku.search.query.Query
import sttp.tapir.*

trait TapirCodecs:
  given Codec[String, Query, CodecFormat.TextPlain] =
    Codec.string.mapEither(Query.parse(_))(_.render)

  given Codec[String, EntityType, CodecFormat.TextPlain] =
    Codec.string.mapEither(EntityType.fromString(_))(_.name)

  given Codec[String, AuthToken.JwtToken, CodecFormat.TextPlain] =
    Codec.string.map(AuthToken.JwtToken(_))(_.render)

  given Codec[String, AuthToken.AnonymousId, CodecFormat.TextPlain] =
    Codec.string.map(s => AuthToken.AnonymousId(Id(s)))(_.render)

object TapirCodecs extends TapirCodecs
