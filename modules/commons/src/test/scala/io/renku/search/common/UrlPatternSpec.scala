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

import io.renku.search.common.UrlPattern.{Segment, UrlParts}
import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Prop

class UrlPatternSpec extends ScalaCheckSuite:

  test("read parts"):
    assertEquals(UrlPattern.splitUrl(""), UrlParts(None, Nil, None, Nil))
    assertEquals(
      UrlPattern.splitUrl("test.com"),
      UrlParts(None, List("test", "com"), None, Nil)
    )
    assertEquals(
      UrlPattern.splitUrl("*.test.com"),
      UrlParts(None, List("*", "test", "com"), None, Nil)
    )
    assertEquals(
      UrlPattern.splitUrl("test.com:123"),
      UrlParts(None, List("test", "com"), Some("123"), Nil)
    )
    assertEquals(
      UrlPattern.splitUrl("test.com:123/auth"),
      UrlParts(None, List("test", "com"), Some("123"), List("auth"))
    )
    assertEquals(
      UrlPattern.splitUrl("https://test.com:123/auth"),
      UrlParts(Some("https"), List("test", "com"), Some("123"), List("auth"))
    )
    assertEquals(
      UrlPattern.splitUrl("/auth/exec"),
      UrlParts(None, Nil, None, List("auth", "exec"))
    )

  test("fromString"):
    assertEquals(
      UrlPattern.fromString("http://"),
      UrlPattern.all.copy(scheme = Some(Segment.Literal("http")))
    )
    assertEquals(
      UrlPattern.fromString("http"),
      UrlPattern.all.copy(host = List(Segment.Literal("http")))
    )
    assertEquals(UrlPattern.fromString("*"), UrlPattern.all)
    assertEquals(UrlPattern.fromString(""), UrlPattern.all)
    assertEquals(
      UrlPattern.fromString("*.*"),
      UrlPattern(
        None,
        List(Segment.MatchAll, Segment.MatchAll),
        None,
        Nil
      )
    )
    assertEquals(
      UrlPattern.fromString("*.test.com"),
      UrlPattern(
        None,
        List(Segment.MatchAll, Segment.Literal("test"), Segment.Literal("com")),
        None,
        Nil
      )
    )
    assertEquals(
      UrlPattern.fromString("*test.com"),
      UrlPattern(
        None,
        List(Segment.Suffix("test"), Segment.Literal("com")),
        None,
        Nil
      )
    )
    assertEquals(
      UrlPattern.fromString("*test.com/auth*"),
      UrlPattern(
        None,
        List(Segment.Suffix("test"), Segment.Literal("com")),
        None,
        List(Segment.Prefix("auth"))
      )
    )
    assertEquals(
      UrlPattern.fromString("https://test.com:15*/auth/sign"),
      UrlPattern(
        Some(Segment.Literal("https")),
        List(Segment.Literal("test"), Segment.Literal("com")),
        Some(Segment.Prefix("15")),
        List(Segment.Literal("auth"), Segment.Literal("sign"))
      )
    )

  property("read valid url pattern") {
    Prop.forAll(CommonGenerators.urlPatternGen) { pattern =>
      val parsed = UrlPattern.fromString(pattern.render)
      val result = parsed == pattern
      if (!result) {
        println(s"Given: $pattern   Parsed: ${parsed}   Rendered: ${pattern.render}")
      }
      result
    }
  }

  property("match all for all patterns") {
    Prop.forAll(CommonGenerators.urlPatternGen) { pattern =>
      val result = UrlPattern.all.matches(pattern.render)
      if (!result) {
        println(s"Failed pattern: ${pattern.render}")
      }
      result
    }
  }

  test("matches successful"):
    List(
      UrlPattern.fromString("*.test.com") -> List(
        "dev.test.com",
        "http://sub.test.com/ab/cd"
      ),
      UrlPattern.fromString("/auth/renku") -> List(
        "dev.test.com/auth/renku",
        "http://sub.test.com/auth/renku"
      ),
      UrlPattern.fromString("*.test.com/auth/renku") -> List(
        "http://dev.test.com/auth/renku",
        "sub1.test.com/auth/renku"
      )
    ).foreach { case (pattern, values) =>
      values.foreach(v =>
        assert(
          pattern.matches(v),
          s"Pattern ${pattern.render} did not match $v, but it should"
        )
      )
    }

  test("matches not successful"):
    List(
      UrlPattern.fromString("*.test.com") -> List(
        "fest.com",
        "http://sub.fest.com/ab/cd"
      ),
      UrlPattern.fromString("/auth/renku") -> List(
        "fest.com/tauth/renku",
        "http://sub.test.com/auth/renkuu"
      ),
      UrlPattern.fromString("*.test.com/auth/renku") -> List(
        "http://dev.test.com/auth",
        "sub1.sub2.test.com/auth/renku"
      )
    ).foreach { case (pattern, values) =>
      values.foreach(v =>
        assert(
          !pattern.matches(v),
          s"Pattern ${pattern.render} matched $v, but it should not"
        )
      )
    }
