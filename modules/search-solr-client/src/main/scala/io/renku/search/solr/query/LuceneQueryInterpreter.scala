package io.renku.search.solr.query

import cats.Monad
import cats.effect.Sync
import cats.syntax.all.*

import io.renku.search.query.Query
import io.renku.search.solr.SearchRole

/** Provides conversion into solrs standard query. See
  * https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
  */
final class LuceneQueryInterpreter[F[_]: Monad]
    extends QueryInterpreter[F]
    with LuceneQueryEncoders:
  private val encoder = SolrTokenEncoder[F, Query]

  def run(ctx: Context[F], query: Query): F[SolrQuery] =
    encoder.encode(ctx, query).map(_.emptyToAll)

object LuceneQueryInterpreter:
  def forSync[F[_]: Sync](role: SearchRole): QueryInterpreter.WithContext[F] =
    QueryInterpreter.withContext(LuceneQueryInterpreter[F], Context.forSync[F](role))
