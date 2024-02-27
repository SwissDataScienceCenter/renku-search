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

package io.renku.scalacheck

import cats.{Functor, Semigroupal}
import org.scalacheck.Gen

object all extends all

trait all:

  extension [V](gen: Gen[V])
    def generateOne: V = gen.sample.getOrElse(generateOne)
    def generateAs[D](f: V => D): D = f(generateOne)

  given Functor[Gen] = new Functor[Gen]:
    override def map[A, B](fa: Gen[A])(f: A => B): Gen[B] = fa.map(f)

  given Semigroupal[Gen] = new Semigroupal[Gen] {
    override def product[A, B](fa: Gen[A], fb: Gen[B]): Gen[(A, B)] =
      fa.flatMap(a => fb.map(a -> _))
  }
