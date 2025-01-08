package io.renku.search.http.borer

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

import io.bullet.borer.*
import scodec.bits.ByteVector

object StreamProvider:

  def apply[F[_]: Sync](
      in: Stream[F, Byte]
  ): F[Input[Array[Byte]]] =
    in.compile.to(ByteVector).map(bv => Input.fromByteArray(bv.toArray))
