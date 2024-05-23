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

import io.renku.search.common.UrlPattern.Segment

final case class UrlPattern(
    scheme: Option[Segment],
    host: List[Segment],
    port: Option[Segment],
    path: List[Segment]
):
  def matches(url: String): Boolean =
    val parts = UrlPattern.splitUrl(url)
    scheme.forall(s => parts.scheme.exists(s.matches)) &&
    (host.isEmpty || host.length == parts.host.length) &&
    host.zip(parts.host).forall { case (s, h) => s.matches(h) } &&
    port.forall(p => parts.port.exists(p.matches)) &&
    (path.isEmpty || path.length == parts.path.length) &&
    path.zip(parts.path).forall { case (s, p) => s.matches(p) }

  def render: String =
    scheme.map(s => s"${s.render}://").getOrElse("") +
      host.map(_.render).mkString(".") +
      port.map(p => s":${p.render}").getOrElse("") +
      (if (path.isEmpty) "" else path.map(_.render).mkString("/", "/", ""))

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
        UrlParts(scheme, host, port, rest)
      case _ =>
        val (host, port) = readHostPort(rest0)
        UrlParts(scheme, host, port, Nil)
  }

  def fromString(str: String): UrlPattern =
    if (str == "*" || str.isEmpty) UrlPattern.all
    else {
      val parts = splitUrl(str)
      UrlPattern(
        parts.scheme.map(Segment.fromString),
        parts.host.map(Segment.fromString),
        parts.port.map(Segment.fromString),
        parts.path.map(Segment.fromString)
      )
    }

  enum Segment:
    case Literal(value: String)
    case Prefix(value: String)
    case Suffix(value: String)
    case MatchAll

    def matches(value: String): Boolean = this match
      case Literal(v) => v.equalsIgnoreCase(value)
      case Prefix(v)  => value.startsWith(v)
      case Suffix(v)  => value.endsWith(v)
      case MatchAll   => true

    def render: String = this match
      case Literal(v) => v
      case Prefix(v)  => s"${v}*"
      case Suffix(v)  => s"*${v}"
      case MatchAll   => "*"

  object Segment:
    def fromString(s: String): Segment = s match
      case "*"                    => MatchAll
      case x if x.startsWith("*") => Suffix(x.drop(1))
      case x if x.endsWith("*")   => Prefix(x.dropRight(1))
      case _                      => Literal(s)
