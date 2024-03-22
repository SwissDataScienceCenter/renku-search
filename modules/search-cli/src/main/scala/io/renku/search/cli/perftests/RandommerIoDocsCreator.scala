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
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.borer.BorerEntityJsonCodec.given
import io.renku.search.model.{Name, projects, users}
import io.renku.search.solr.documents.{Project, User}
import org.http4s.MediaType.application
import org.http4s.Method.{GET, POST}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Accept
import org.http4s.implicits.*
import org.http4s.{Header, MediaType, Method, Uri}
import org.typelevel.ci.*

/** For the API go here: https://randommer.io/api/swagger-docs/index.html */
object RandommerIoDocsCreator:
  def make[F[_]: Async: Network: ModelTypesGenerators](
      apiKey: String
  ): Resource[F, DocumentsCreator[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map(new RandommerIoDocsCreator[F](_, apiKey))

private class RandommerIoDocsCreator[F[_]: Async: ModelTypesGenerators](
    client: Client[F],
    apiKey: String,
    chunkSize: Int = 20
) extends DocumentsCreator[F]
    with HttpClientDsl[F]:

  private val gens = ModelTypesGenerators[F]

  override def findUser: Stream[F, User] =
    Stream.evals(getUserNames).evalMap(toUser) ++ findUser

  private lazy val getUserNames =
    val req = get(
      (api / "name")
        .withQueryParam("nameType", "fullname")
        .withQueryParam("quantity", chunkSize)
    )
    client.expect[List[String]](req).map(_.flatMap(toFirstAndLast))

  private lazy val toUser: ((users.FirstName, users.LastName)) => F[User] = {
    case (first, last) =>
      gens.generateId.map(id =>
        User(id, first.some, last.some, Name(s"$first $last").some)
      )
  }

  override def findProject: Stream[F, (Project, List[User])] =
    Stream
      .evals(getNames)
      .zip(findUser.chunkN(2, allowFewer = false).map(_.toList))
      .evalMap(toProject) ++ findProject

  private lazy val toProject: ((Name, List[User])) => F[(Project, List[User])] = {
    case (name, all @ user :: users) =>
      (
        gens.generateId,
        getDescription,
        gens.generateCreationDate
      ).mapN { case (id, desc, creationDate) =>
        val slug = createSlug(name, user)
        Project(
          id,
          name,
          slug,
          Seq(createRepo(slug)),
          projects.Visibility.Public,
          desc,
          createdBy = user.id,
          creationDate
        ) -> all
      }
    case (name, Nil) =>
      new Exception("No users generated").raiseError[F, (Project, List[User])]
  }

  private def createSlug(name: Name, user: User) =
    projects.Slug {
      val nameConditioned = name.value.replace(" ", "_")
      val namespace = user.name.map(_.value.replace(" ", "_")).getOrElse(nameConditioned)
      s"$namespace/$nameConditioned"
    }

  private def createRepo(slug: projects.Slug) =
    projects.Repository(s"https://github.com/$slug")

  private lazy val getNames =
    val req = get(
      (api / "name" / "suggestions")
        .withQueryParam("startingWords", "project proj")
    )
    client.expect[List[String]](req).map(_.map(Name.apply))

  private lazy val getDescription =
    val req = post(
      (api / "Text" / "Review")
        .withQueryParam("product", "renku")
        .withQueryParam("quantity", "1")
    )
    client.expect[List[String]](req).map(_.headOption.map(projects.Description.apply))

  private lazy val api = uri"https://randommer.io/api"

  private def get(uri: Uri) =
    GET(uri)
      .putHeaders(Accept(application.json))
      .putHeaders(Header.Raw(ci"X-Api-Key", apiKey))

  private def post(uri: Uri) =
    POST(uri)
      .putHeaders(Accept(application.json))
      .putHeaders(Header.Raw(ci"X-Api-Key", apiKey))

  private def toFirstAndLast(v: String): Option[(users.FirstName, users.LastName)] =
    v.split(' ').toList match {
      case f :: r => Some(users.FirstName(f) -> users.LastName(r.mkString(" ")))
      case _      => None
    }
