package io.renku.search.cli.perftests

import java.time.Instant

import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.derivation.key
import io.renku.json.codecs.DateTimeDecoders
import io.renku.search.model.Visibility

final private case class GitLabProject(
    id: Int,
    description: Option[String] = None,
    name: String,
    path_with_namespace: String,
    http_url_to_repo: String,
    created_at: Instant,
    @key("tag_list") tagList: List[String],
    topics: List[String]
):
  val visibility: Visibility = Visibility.Public

  lazy val tagsAndTopics: List[String] =
    (tagList ::: topics).distinct

  lazy val namespace: String =
    path_with_namespace.lastIndexOf('/') match
      case n if n > 0 => path_with_namespace.drop(n)
      case _          => path_with_namespace

private object GitLabProject extends DateTimeDecoders:
  given Decoder[GitLabProject] = deriveDecoder

final private case class GitLabProjectUser(id: Int, name: String, username: String)

private object GitLabProjectUser:
  given Decoder[GitLabProjectUser] = deriveDecoder
