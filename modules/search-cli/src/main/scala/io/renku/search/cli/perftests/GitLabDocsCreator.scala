package io.renku.search.cli.perftests

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network

import io.bullet.borer.Decoder
import io.renku.search.events.syntax.*
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.borer.BorerEntityJsonCodec.given
import io.renku.search.model.*
import io.renku.search.solr.documents.{Project, User}
import io.renku.solr.client.DocVersion
import org.http4s.*
import org.http4s.MediaType.application
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Accept

private object GitLabDocsCreator:
  def make[F[_]: Async: Network: ModelTypesGenerators](
      gitLabUri: Uri
  ): Resource[F, DocumentsCreator[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map(new GitLabDocsCreator[F](_, gitLabUri))

private class GitLabDocsCreator[F[_]: Async: ModelTypesGenerators](
    client: Client[F],
    gitLabUri: Uri
) extends DocumentsCreator[F]
    with HttpClientDsl[F]:

  override def findUser: Stream[F, User] =
    Stream.empty

  override def findProject: Stream[F, (Project, List[User])] =
    Stream
      .iterate(0)(_ + 1)
      .evalMap(getProjects)
      .takeWhile(_.nonEmpty)
      .flatMap(Stream.emits)
      .evalMap(gp => findProjectUsers(gp.id).compile.toList.map(_.distinct).tupleLeft(gp))
      .map(toProject)
      .unNone

  private def getProjects(page: Int) =
    val req = get(
      (apiV4 / "projects")
        .withQueryParam("page", page)
        .withQueryParam("order_by", "created_at")
        .withQueryParam("sort", "asc")
    )
    client.expect[List[GitLabProject]](req)

  private def toProject(
      glProj: GitLabProject,
      users: List[User]
  ): Option[(Project, List[User])] =
    users match
      case Nil => None
      case creator :: all =>
        val p = Project(
          id = Id(s"gl_proj_${glProj.id}"),
          name = glProj.name.toName,
          slug = glProj.path_with_namespace.toSlug,
          repositories = Seq(glProj.http_url_to_repo.toRepository),
          visibility = glProj.visibility,
          description = glProj.description.map(_.toDescription),
          createdBy = creator.id,
          creationDate = glProj.created_at.toCreationDate,
          keywords = glProj.tagsAndTopics.map(_.toKeyword),
          namespace = glProj.namespace.toNamespace.some
        )
        Some(p -> users)

  private def findProjectUsers(projectId: Int) =
    Stream
      .iterate(0)(_ + 1)
      .evalMap(getProjectUsers(projectId, _))
      .takeWhile(_.nonEmpty)
      .flatMap(Stream.emits)
      .filterNot(_.name `contains` "_bot_")
      .filterNot(_.name `contains` "****")
      .map(toUser)

  private def getProjectUsers(id: Int, page: Int) =
    val req = get(
      (apiV4 / "projects" / id / "users")
        .withQueryParam("page", page)
    )
    client.expect[List[GitLabProjectUser]](req)

  private def toUser(u: GitLabProjectUser): User =
    val firstAndLast = toFirstAndLast(u.name.trim)
    User(
      id = Id(s"gl_user_${u.id}"),
      namespace = u.username.toNamespace.some,
      firstName = firstAndLast.map(_._1).flatMap {
        case v if v.value.trim.isBlank => None
        case v                         => v.some
      },
      lastName = firstAndLast.map(_._2).flatMap {
        case v if v.value.trim.isBlank => None
        case v                         => v.some
      }
    )

  private lazy val apiV4 = gitLabUri / "api" / "v4"

  private def get(uri: Uri) =
    GET(uri)
      .putHeaders(Accept(application.json))

  private def toFirstAndLast(v: String): Option[(FirstName, LastName)] =
    v.trim.split(' ').toList match {
      case f :: r => Some(FirstName(f) -> LastName(r.mkString(" ")))
      case _      => None
    }
