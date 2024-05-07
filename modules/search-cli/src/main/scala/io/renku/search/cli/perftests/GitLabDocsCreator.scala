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

package io.renku.search.cli.perftests

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network

import io.bullet.borer.Decoder
import io.github.arainko.ducktape.*
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.borer.BorerEntityJsonCodec.given
import io.renku.search.model.*
import io.renku.search.model.Namespace
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
      .evalMap(toProject)
      .unNone

  private def getProjects(page: Int) =
    val req = get(
      (apiV4 / "projects")
        .withQueryParam("page", page)
        .withQueryParam("order_by", "created_at")
        .withQueryParam("sort", "asc")
    )
    client.expect[List[GitLabProject]](req)

  private lazy val toProject
      : ((GitLabProject, List[User])) => F[Option[(Project, List[User])]] = {
    case (glProj, all @ user :: users) =>
      (glProj
        .into[Project]
        .transform(
          Field.default(_.namespace),
          Field.computed(_.keywords, _.tagsAndTopics.map(Keyword.apply)),
          Field.default(_.version),
          Field.computed(_.id, s => Id(s"gl_proj_${s.id}")),
          Field.computed(_.slug, s => projects.Slug(s.path_with_namespace)),
          Field
            .computed(_.repositories, s => Seq(projects.Repository(s.http_url_to_repo))),
          Field.computed(_.visibility, s => s.visibility),
          Field.computed(_.createdBy, s => user.id),
          Field.computed(_.creationDate, s => projects.CreationDate(s.created_at)),
          Field.default(_.owners),
          Field.default(_.editors),
          Field.default(_.viewers),
          Field.default(_.groupOwners),
          Field.default(_.groupEditors),
          Field.default(_.groupViewers),
          Field.default(_.members),
          Field.default(_.score)
        ) -> all).some.pure[F]
    case (name, Nil) =>
      Option.empty.pure[F]
  }

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

  private def toUser(glUser: GitLabProjectUser): User =
    val firstAndLast = toFirstAndLast(glUser.name.trim)
    glUser
      .into[User]
      .transform(
        Field.computed(_.namespace, u => Namespace(u.username).some),
        Field.default(_.version),
        Field.computed(_.id, s => Id(s"gl_user_${s.id}")),
        Field.computed(
          _.firstName,
          s =>
            firstAndLast.map(_._1).flatMap {
              case v if v.value.trim.isBlank => None
              case v                         => v.some
            }
        ),
        Field.computed(
          _.lastName,
          s =>
            firstAndLast.map(_._2).flatMap {
              case v if v.value.trim.isBlank => None
              case v                         => v.some
            }
        ),
        Field.default(_.score)
      )

  private lazy val apiV4 = gitLabUri / "api" / "v4"

  private def get(uri: Uri) =
    GET(uri)
      .putHeaders(Accept(application.json))

  private def toFirstAndLast(v: String): Option[(users.FirstName, users.LastName)] =
    v.trim.split(' ').toList match {
      case f :: r => Some(users.FirstName(f) -> users.LastName(r.mkString(" ")))
      case _      => None
    }
