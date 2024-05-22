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

package io.renku.search.common

import cats.data.NonEmptyList

import io.renku.search.GeneratorSyntax.*
import org.scalacheck.Gen

object CommonGenerators:
  def nelOfN[A](n: Int, gen: Gen[A]): Gen[NonEmptyList[A]] =
    for {
      e0 <- gen
      en <- Gen.listOfN(n - 1, gen)
    } yield NonEmptyList(e0, en)

  def urlPatternGen: Gen[UrlPattern] =
    def segmentGen(inner: Gen[String]): Gen[UrlPattern.Segment] =
      Gen.oneOf(
        inner.map(s => UrlPattern.Segment.Prefix(s)),
        inner.map(s => UrlPattern.Segment.Suffix(s)),
        inner.map(s => UrlPattern.Segment.Literal(s)),
        Gen.const(UrlPattern.Segment.MatchAll)
      )

    val schemes = segmentGen(Gen.oneOf("http", "https"))
    val ports = segmentGen(Gen.oneOf("123", "8080", "8145", "487", "11"))
    val hosts = segmentGen(
      Gen.oneOf("test", "com", "ch", "de", "dev", "renku", "penny", "cycle")
    ).asListOfN(0, 5)
    val paths = segmentGen(
      Gen.oneOf("auth", "authenticate", "doAuth", "me", "run", "api")
    ).asListOfN(0, 5)
    for
      scheme <- schemes.asOption
      host <- hosts
      port <- ports.asOption
      path <- paths
    yield UrlPattern(scheme, host, port, path)
