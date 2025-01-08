package io.renku.search.api.tapir

import cats.syntax.all.*

import io.renku.search.api.data.*
import io.renku.search.http.borer.TapirBorerJson
import io.renku.search.query.Query
import sttp.tapir.{query as queryParam, *}

object Params extends TapirCodecs with TapirBorerJson with ApiSchema {

  val query: EndpointInput[Query] =
    queryParam[Query]("q").description("User defined search query").default(Query.empty)

  val pageDef: EndpointInput[PageDef] = {
    val page =
      queryParam[Int]("page")
        .validate(Validator.min(1))
        .description("The page to retrieve, starting at 1")
        .default(1)

    val perPage =
      queryParam[Int]("per_page")
        .description("How many items to return for one page")
        .validate(Validator.min(1))
        .validate(Validator.max(100))
        .default(PageDef.default.limit)

    (page / perPage).map(PageDef.fromPage.tupled)(p => (p.page, p.limit))
  }

  val queryInput: EndpointInput[QueryInput] = query.and(pageDef).mapTo[QueryInput]

  val pagingInfo: EndpointOutput[PageWithTotals] = {
    val perPage: EndpointOutput[Int] = header[Int]("x-per-page")
    val page: EndpointOutput[Int] = header[Int]("x-page")
    val pageDef =
      page.and(perPage).map(PageDef.fromPage.tupled)(pd => (pd.page, pd.limit))

    val prevPage: EndpointOutput[Option[Int]] = header[Option[Int]]("x-prev-page")
    val nextPage: EndpointOutput[Option[Int]] = header[Option[Int]]("x-next-page")
    val total: EndpointOutput[Long] = header[Long]("x-total")
    val totalPages: EndpointOutput[Int] = header[Int]("x-total-pages")

    pageDef.and(total).and(totalPages).and(prevPage).and(nextPage).mapTo[PageWithTotals]
  }

  val searchItems: EndpointOutput[Seq[SearchEntity]] =
    borerJsonBody[Seq[SearchEntity]]

  val searchResult: EndpointOutput[SearchResult] =
    borerJsonBody[SearchResult].and(pagingInfo).map(_._1)(r => (r, r.pagingInfo))

  private val renkuAuthIdToken: EndpointInput[Option[AuthToken.JwtToken]] =
    auth.bearer[Option[String]]().map(_.map(t => AuthToken.JwtToken(t)))(_.map(_.token))

  private val renkuAuthAnonId: EndpointInput[Option[AuthToken.AnonymousId]] =
    header[Option[AuthToken.AnonymousId]]("Renku-Auth-Anon-Id")

  val renkuAuth: EndpointInput[AuthToken] =
    (renkuAuthIdToken / renkuAuthAnonId).map { case (token, id) =>
      token.orElse(id).getOrElse(AuthToken.None)
    }(_.fold(a => (a.some, None), b => (None, b.some), (None, None)))
}
