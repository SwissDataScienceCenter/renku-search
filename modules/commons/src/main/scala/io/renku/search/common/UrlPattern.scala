package io.renku.search.common

import io.renku.search.common.UrlPattern.Segment

final case class UrlPattern(
    scheme: Option[Segment],
    host: List[Segment],
    port: Option[Segment],
    path: List[Segment]
):
  def isMatchAll: Boolean =
    scheme.forall(_.isMatchAll) &&
      host.forall(_.isMatchAll) &&
      port.forall(_.isMatchAll) &&
      path.forall(_.isMatchAll)

  def matches(url: String): Boolean =
    val parts = UrlPattern.splitUrl(url)
    scheme.forall(s => parts.scheme.exists(s.matches)) &&
    matchList(parts.host, host) &&
    port.forall(p => parts.port.exists(p.matches)) &&
    matchList(parts.path, path)

  def render: String =
    if (isMatchAll) "*"
    else
      scheme.map(s => s"${s.render}://").getOrElse("") +
        host.map(_.render).mkString(".") +
        port.map(p => s":${p.render}").getOrElse("") +
        (if (path.isEmpty) "" else path.map(_.render).mkString("/", "/", ""))

  private def matchList(values: List[String], pattern: List[Segment]): Boolean =
    pattern.isEmpty || {
      pattern.indexOf(Segment.MatchAllRemainder) match
        case n if n < 0 =>
          values.lengthIs == pattern.length && pattern.zip(values).forall { case (s, h) =>
            s.matches(h)
          }
        case n =>
          values.sizeIs >= n &&
          pattern.take(n).zip(values).forall { case (s, h) => s.matches(h) }
    }

object UrlPattern:
  val all: UrlPattern = UrlPattern(None, Nil, None, Nil)

  final private[common] case class UrlParts(
      scheme: Option[String],
      host: List[String],
      port: Option[String],
      path: List[String]
  )
  private[common] def splitUrl(url: String): UrlParts = {
    def readScheme(s: String): (Option[String], String) =
      if (!s.contains("://")) (None, s)
      else
        s.split("://").filter(_.nonEmpty).toList match
          case first :: rest :: Nil => (Some(first), rest)
          case first                => (Some(first.mkString), "")

    def readHostPort(s: String): (List[String], Option[String]) =
      s.split(':').toList match
        case h :: p :: _ =>
          (h.split('.').filter(_.nonEmpty).toList, Option(p).filter(_.nonEmpty))
        case rest =>
          (s.split('.').filter(_.nonEmpty).toList, None)

    val (scheme, rest0) = readScheme(url)
    rest0.split('/').toList match
      case hp :: rest =>
        val (host, port) = readHostPort(hp)
        UrlParts(scheme, host, port, rest.filter(_.nonEmpty))
      case _ =>
        val (host, port) = readHostPort(rest0)
        UrlParts(scheme, host, port, Nil)
  }

  def fromString(str: String): Either[String, UrlPattern] =
    if (str.isEmpty) Left("empty url pattern")
    else if ("*" == str || "**" == str) Right(UrlPattern.all)
    else {
      val parts = splitUrl(str)
      // specifiyng anything after a '**' doesn't make sense, so we detect
      // it here and fail to convert to a pattern
      // (no ** || last = **)
      def check(segs: List[Segment]) =
        val (pre, suf) = segs.span(_ != Segment.MatchAllRemainder)
        if (suf.sizeIs <= 1) Right(segs)
        else Left("A '**' should not be followed by other segments")

      for
        host <- check(parts.host.map(Segment.fromString))
        path <- check(parts.path.map(Segment.fromString))
        scheme = parts.scheme.map(Segment.fromString)
        port = parts.port.map(Segment.fromString)
      yield UrlPattern(scheme, host, port, path)
    }

  def unsafeFromString(str: String): UrlPattern =
    fromString(str).fold(sys.error, identity)

  enum Segment:
    case Literal(value: String)
    case Prefix(value: String)
    case Suffix(value: String)
    case MatchAll
    case MatchAllRemainder

    def matches(value: String): Boolean = this match
      case Literal(v)        => v.equalsIgnoreCase(value)
      case Prefix(v)         => value.startsWith(v)
      case Suffix(v)         => value.endsWith(v)
      case MatchAll          => true
      case MatchAllRemainder => true

    private[common] def isMatchAll: Boolean = this match
      case Literal(v)        => false
      case Prefix(v)         => false
      case Suffix(v)         => false
      case MatchAll          => true
      case MatchAllRemainder => true

    def render: String = this match
      case Literal(v)        => v
      case Prefix(v)         => s"${v}*"
      case Suffix(v)         => s"*${v}"
      case MatchAll          => "*"
      case MatchAllRemainder => "**"

  object Segment:
    def fromString(s: String): Segment = s match
      case "**"                   => MatchAllRemainder
      case "*"                    => MatchAll
      case x if x.startsWith("*") => Suffix(x.drop(1))
      case x if x.endsWith("*")   => Prefix(x.dropRight(1))
      case _                      => Literal(s)
