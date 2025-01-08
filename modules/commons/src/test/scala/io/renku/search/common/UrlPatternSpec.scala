package io.renku.search.common

import io.renku.search.common.UrlPattern.{Segment, UrlParts}
import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Prop

class UrlPatternSpec extends ScalaCheckSuite:
  def urlPattern(str: String) = UrlPattern.unsafeFromString(str)

  test("read parts") {
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
    assertEquals(
      UrlPattern.splitUrl("https://test.com:123/auth//**"),
      UrlParts(Some("https"), List("test", "com"), Some("123"), List("auth", "**"))
    )
  }

  test("fromString successful") {
    assertEquals(urlPattern("*"), UrlPattern.all)
    assertEquals(urlPattern("**"), UrlPattern.all)
    assertEquals(
      urlPattern("http://"),
      UrlPattern.all.copy(scheme = Some(Segment.Literal("http")))
    )
    assertEquals(
      urlPattern("http"),
      UrlPattern.all.copy(host = List(Segment.Literal("http")))
    )
    assertEquals(urlPattern("*"), UrlPattern.all)
    assertEquals(
      urlPattern("*.*"),
      UrlPattern(
        None,
        List(Segment.MatchAll, Segment.MatchAll),
        None,
        Nil
      )
    )
    assertEquals(
      urlPattern("*.test.com"),
      UrlPattern(
        None,
        List(Segment.MatchAll, Segment.Literal("test"), Segment.Literal("com")),
        None,
        Nil
      )
    )
    assertEquals(
      urlPattern("*test.com"),
      UrlPattern(
        None,
        List(Segment.Suffix("test"), Segment.Literal("com")),
        None,
        Nil
      )
    )
    assertEquals(
      urlPattern("*test.com/auth*"),
      UrlPattern(
        None,
        List(Segment.Suffix("test"), Segment.Literal("com")),
        None,
        List(Segment.Prefix("auth"))
      )
    )
    assertEquals(
      urlPattern("https://test.com:15*/auth/sign"),
      UrlPattern(
        Some(Segment.Literal("https")),
        List(Segment.Literal("test"), Segment.Literal("com")),
        Some(Segment.Prefix("15")),
        List(Segment.Literal("auth"), Segment.Literal("sign"))
      )
    )
    assertEquals(
      urlPattern("https://test.com/**"),
      UrlPattern(
        Some(Segment.Literal("https")),
        List(Segment.Literal("test"), Segment.Literal("com")),
        None,
        List(Segment.MatchAllRemainder)
      )
    )
    assertEquals(
      urlPattern("https://test.com/abc/**"),
      UrlPattern(
        Some(Segment.Literal("https")),
        List(Segment.Literal("test"), Segment.Literal("com")),
        None,
        List(Segment.Literal("abc"), Segment.MatchAllRemainder)
      )
    )
  }

  test("fromString fail") {
    assert(UrlPattern.fromString("").isLeft)
    assert(UrlPattern.fromString("test.com/a/**/b").isLeft)
    assert(UrlPattern.fromString("**.com/a/b").isLeft)
  }

  test("read selected valid url pattern") {
    assert(urlPattern("*").isMatchAll)
    assert(urlPattern("**").isMatchAll)
  }

  test("render patterns") {
    assertEquals(UrlPattern.all.render, "*")
    assertEquals(urlPattern("**").render, "*")
  }

  property("read valid url pattern") {
    Prop.forAll(CommonGenerators.urlPatternGen) { pattern =>
      val parsed = urlPattern(pattern.render)
      val result = (parsed.isMatchAll && pattern.isMatchAll) || (parsed == pattern)
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
      urlPattern("*.test.com") -> List(
        "dev.test.com",
        "http://sub.test.com/ab/cd"
      ),
      urlPattern("/auth/renku") -> List(
        "dev.test.com/auth/renku",
        "http://sub.test.com/auth/renku"
      ),
      urlPattern("*.test.com/auth/renku") -> List(
        "http://dev.test.com/auth/renku",
        "sub1.test.com/auth/renku"
      ),
      urlPattern("https://renku.com/auth/**") -> List(
        "https://renku.com/auth/realms/Renku"
      ),
      urlPattern("test.com/**") -> List(
        "http://test.com/auth/a/b",
        "https://test.com/auth",
        "https://test.com",
        "https://test.com/"
      ),
      urlPattern("test.com/a/c/**") -> List(
        "http://test.com/a/c",
        "http://test.com/a/c/b",
        "http://test.com/a/c/b/1/2"
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
      urlPattern("*.test.com") -> List(
        "fest.com",
        "http://sub.fest.com/ab/cd"
      ),
      urlPattern("/auth/renku") -> List(
        "fest.com/tauth/renku",
        "http://sub.test.com/auth/renkuu"
      ),
      urlPattern("*.test.com/auth/renku") -> List(
        "http://dev.test.com/auth",
        "sub1.sub2.test.com/auth/renku"
      ),
      urlPattern("test.com/a/c/**") -> List(
        "http://test.com/a/b/c",
        "http://test.com/a"
      )
    ).foreach { case (pattern, values) =>
      values.foreach(v =>
        assert(
          !pattern.matches(v),
          s"Pattern ${pattern.render} matched $v, but it should not"
        )
      )
    }
