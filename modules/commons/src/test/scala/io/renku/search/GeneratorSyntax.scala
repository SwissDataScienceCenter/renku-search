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

package io.renku.search

import cats.arrow.FunctionK
import cats.effect.IO
import fs2.Stream
import org.scalacheck.Gen

trait GeneratorSyntax:

  extension [A](self: Gen[A])
    @annotation.tailrec
    final def generateOne: A =
      self.sample match
        case Some(a) => a
        case None    => generateOne

    def generateSome: Option[A] = Some(generateOne)

    def generateList: List[A] = Gen.listOf(self).generateOne

    def stream: Stream[Gen, A] =
      Stream.repeatEval(self)

  extension [A](self: Stream[Gen, A])
    def toIO: Stream[IO, A] =
      self.translate(FunctionK.lift[Gen, IO]([X] => (gx: Gen[X]) => IO(gx.generateOne)))

object GeneratorSyntax extends GeneratorSyntax
