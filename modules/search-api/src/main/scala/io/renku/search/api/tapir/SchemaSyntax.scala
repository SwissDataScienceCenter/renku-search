package io.renku.search.api.tapir

import io.bullet.borer.Encoder
import io.bullet.borer.Json
import sttp.tapir.Schema

trait SchemaSyntax:

  extension [T](self: Schema[T])
    def jsonExample[TT >: T](value: TT)(using Encoder[TT]): Schema[T] =
      self.encodedExample(Json.encode(value).toUtf8String)

object SchemaSyntax extends SchemaSyntax
