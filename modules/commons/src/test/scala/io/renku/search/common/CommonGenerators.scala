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
    val allMatchBoth =
      Gen.oneOf(UrlPattern.Segment.MatchAll, UrlPattern.Segment.MatchAllRemainder)
    val allMatchOnly = Gen.const(UrlPattern.Segment.MatchAll)

    def segmentGen(
        inner: Gen[String],
        other: Gen[UrlPattern.Segment]
    ): Gen[UrlPattern.Segment] =
      Gen.oneOf(
        inner.map(s => UrlPattern.Segment.Prefix(s)),
        inner.map(s => UrlPattern.Segment.Suffix(s)),
        inner.map(s => UrlPattern.Segment.Literal(s)),
        other
      )

    def appendMatchRemainder(
        g: Gen[List[UrlPattern.Segment]]
    ): Gen[List[UrlPattern.Segment]] =
      for
        segs <- g
        rem <- Gen.option(Gen.const(UrlPattern.Segment.MatchAllRemainder))
      yield segs ++ rem.toList

    val schemes = segmentGen(Gen.oneOf("http", "https"), allMatchBoth)
    val ports = segmentGen(Gen.oneOf("123", "8080", "8145", "487", "11"), allMatchBoth)
    val hosts = appendMatchRemainder(
      segmentGen(
        Gen.oneOf("test", "com", "ch", "de", "dev", "renku", "penny", "cycle"),
        allMatchOnly
      ).asListOfN(0, 4)
    )
    val paths = appendMatchRemainder(
      segmentGen(
        Gen.oneOf("auth", "authenticate", "doAuth", "me", "run", "api"),
        allMatchOnly
      ).asListOfN(0, 4)
    )
    for
      scheme <- schemes.asOption
      host <- hosts
      port <- ports.asOption
      path <- paths
    yield UrlPattern(scheme, host, port, path)
