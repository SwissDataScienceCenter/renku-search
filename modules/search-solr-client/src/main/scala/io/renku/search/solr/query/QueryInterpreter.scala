package io.renku.search.solr.query

import io.renku.search.query.Query

trait QueryInterpreter[F[_]]:
  def run(ctx: Context[F], q: Query): F[SolrQuery]

object QueryInterpreter:
  trait WithContext[F[_]]:
    def run(q: Query): F[SolrQuery]

  def withContext[F[_]](qi: QueryInterpreter[F], ctx: Context[F]): WithContext[F] =
    new WithContext[F]:
      def run(q: Query) = qi.run(ctx, q)
