/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.search.http.borer

import fs2.*

import _root_.io.bullet.borer.*

/** Caveat: the buffer-size must be large enough to decode at least one A */
object StreamDecode {

  def decodeJson[F[_]: RaiseThrowable, A: Decoder](
      in: Stream[F, Byte],
      bufferSize: Int = 64 * 1024
  ): Stream[F, A] =
    decode0[F, A](ba => Json.decode(ba).withPrefixOnly.to[A])(in, bufferSize)

  private def decode0[F[_]: RaiseThrowable, A](
      decode: Input[Array[Byte]] => DecodingSetup.Sealed[A]
  )(
      in: Stream[F, Byte],
      bufferSize: Int
  ): Stream[F, A] =
    in.dropLastIf(_ == 10)
      .repeatPull(_.unconsN(bufferSize, allowFewer = true).flatMap {
        case Some((hd, tl)) =>
          decodeCont[A](decode)(hd) match {
            case Right((v, remain)) =>
              Pull.output(Chunk.from(v)).as(Some(Stream.chunk(remain) ++ tl))
            case Left(ex) =>
              Pull.raiseError(ex)
          }
        case None =>
          Pull.pure(None)
      })

  private val curlyClose = Chunk('}'.toByte)
  private def decodeCont[A](
      decode: Input[Array[Byte]] => DecodingSetup.Sealed[A]
  )(input: Chunk[Byte]): Either[Throwable, (Vector[A], Chunk[Byte])] = {
    @annotation.tailrec
    def go(
        in: Input[Array[Byte]],
        pos: Int,
        result: Vector[A]
    ): Either[Throwable, (Vector[A], Chunk[Byte])] =
      decode(in).valueAndInputEither match
        case Right((v, rest)) =>
          val rb = rest.asInstanceOf[Input[Array[Byte]]]
          val nextPos = rb.cursor.toInt
          go(rb.unread(1), nextPos - 1, result :+ v)

        case Left(ex) =>
          if (result.isEmpty) Left(ex)
          else {
            // the position seems sometimes off by 1, standing on the last char closing the json object
            val hack = {
              val next = input.drop(pos)
              if (next == curlyClose) Chunk.empty
              else next
            }
            Right(result -> hack)
          }

    go(Input.fromByteArray(input.toArray), 0, Vector.empty)
  }
}
