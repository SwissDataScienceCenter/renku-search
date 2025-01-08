package io.renku.search.cli.perftests

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network

import io.bullet.borer.Decoder
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.borer.BorerEntityJsonCodec.given
import io.renku.search.model.*
import io.renku.search.solr.documents.{Project, User}
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import org.http4s.*
import org.http4s.MediaType.application
import org.http4s.Method.{GET, POST}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Accept
import org.http4s.implicits.*
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
    chunkSize: Int = 50
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

  private lazy val toUser: ((FirstName, LastName)) => F[User] = { case (first, last) =>
    gens.generateId.map(id =>
      User(id, DocVersion.NotExists, first.some, last.some, Name(s"$first $last").some)
    )
  }

  override def findProject: Stream[F, (Project, List[User])] =
    findName
      .zip(findDescription)
      .zip(findKeywords(8))
      .zip(findUser)
      .evalMap { case (((name, desc), kws), user) =>
        toProject(name, desc, kws, user)
      } ++ findProject

  private def toProject(
      name: Name,
      desc: Description,
      keywords: List[Keyword],
      user: User
  ): F[(Project, List[User])] =
    (gens.generateId, gens.generateCreationDate).mapN { case (id, creationDate) =>
      val slug = createSlug(name, user)
      Project(
        id = id,
        version = DocVersion.NotExists,
        name = name,
        slug = slug,
        repositories = Seq(createRepo(slug)),
        visibility = Visibility.Public,
        description = Some(desc),
        keywords = keywords,
        createdBy = user.id,
        creationDate = creationDate,
        creatorDetails = Some(ResponseBody.single(user))
      ) -> List(user)
    }

  private def createSlug(name: Name, user: User) =
    Slug {
      val nameConditioned = name.value.replace(" ", "_")
      val namespace = user.name.map(_.value.replace(" ", "_")).getOrElse(nameConditioned)
      s"$namespace/$nameConditioned".toLowerCase
    }

  private def createRepo(slug: Slug) =
    Repository(s"https://github.com/$slug")

  private lazy val findName: Stream[F, Name] =
    Stream.evals(getNameSuggestions).map(Name.apply) ++ findName

  private lazy val getNameSuggestions =
    val req = get(
      (api / "name" / "suggestions")
        .withQueryParam("startingWords", "renku proj project datascience lab")
    )
    client.expect[List[String]](req)

  private lazy val findDescription: Stream[F, Description] =
    Stream
      .evals(getReviews)
      .zip(Stream.evals(getBusinessNames))
      .flatMap { case (l, r) => Stream(l, r) }
      .map(Description.apply) ++ findDescription

  private lazy val getReviews: F[List[String]] =
    val req = post(
      (api / "Text" / "Review")
        .withQueryParam("product", "renku")
        .withQueryParam("quantity", chunkSize)
    )
    client.expect[List[String]](req)

  private lazy val getBusinessNames: F[List[String]] =
    val req = post(
      (api / "name" / "BusinessName")
        .withQueryParam("cultureCode", "en_US")
        .withQueryParam("number", chunkSize)
    )
    client.expect[List[String]](req)

  private lazy val api = uri"https://randommer.io/api"

  private def getBrandNames(max: Int): F[List[String]] =
    val req = post((api / "name" / "BrandName").withQueryParam("startingWords", "sc"))
    client.expect[List[String]](req).map(_.take(max))

  private def findKeywords(max: Int): Stream[F, List[Keyword]] =
    Stream.eval(getBrandNames(max)).map(_.map(Keyword.apply)) ++ findKeywords(max)

  private def get(uri: Uri) =
    GET(uri)
      .putHeaders(Accept(application.json))
      .putHeaders(Header.Raw(ci"X-Api-Key", apiKey))

  private def post(uri: Uri) =
    POST(uri)
      .putHeaders(Accept(application.json))
      .putHeaders(Header.Raw(ci"X-Api-Key", apiKey))

  private def toFirstAndLast(v: String): Option[(FirstName, LastName)] =
    v.split(' ').toList match {
      case f :: r => Some(FirstName(f) -> LastName(r.mkString(" ")))
      case _      => None
    }
