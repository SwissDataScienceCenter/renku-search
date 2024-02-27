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

package io.renku.search.solr.query

import cats.syntax.all.*
import io.renku.search.query.Query.Segment
import io.renku.search.query.FieldTerm
import io.renku.search.query.Field
import io.renku.search.query.Query

import cats.Monad
import cats.Applicative
import io.renku.search.query.Comparison

trait LuceneQueryEncoders:

  given projectIdIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.ProjectIdIs] =
    SolrTokenEncoder.basic { case FieldTerm.ProjectIdIs(ids) =>
      SolrQuery(SolrToken.orFieldIs(Field.ProjectId, ids.map(SolrToken.fromString)))
    }

  given nameIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.NameIs] =
    SolrTokenEncoder.basic { case FieldTerm.NameIs(names) =>
      SolrQuery(SolrToken.orFieldIs(Field.Name, names.map(SolrToken.fromString)))
    }

  given typeIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.TypeIs] =
    SolrTokenEncoder.basic { case FieldTerm.TypeIs(values) =>
      SolrQuery(SolrToken.orFieldIs(Field.Type, values.map(SolrToken.fromEntityType)))
    }

  given slugIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.SlugIs] =
    SolrTokenEncoder.basic { case FieldTerm.SlugIs(names) =>
      SolrQuery(SolrToken.orFieldIs(Field.Slug, names.map(SolrToken.fromString)))
    }

  given createdByIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.CreatedByIs] =
    SolrTokenEncoder.basic { case FieldTerm.CreatedByIs(names) =>
      SolrQuery(SolrToken.orFieldIs(Field.CreatedBy, names.map(SolrToken.fromString)))
    }

  given visibilityIs[F[_]: Applicative]: SolrTokenEncoder[F, FieldTerm.VisibilityIs] =
    SolrTokenEncoder.basic { case FieldTerm.VisibilityIs(values) =>
      SolrQuery(
        SolrToken.orFieldIs(Field.Visibility, values.map(SolrToken.fromVisibility))
      )
    }

  given created[F[_]: Monad]: SolrTokenEncoder[F, FieldTerm.Created] =
    val created = SolrToken.fromField(Field.Created)
    SolrTokenEncoder.create[F, FieldTerm.Created] {
      case (ctx, FieldTerm.Created(Comparison.Is, values)) =>
        (ctx.currentTime, ctx.zoneId).mapN { (ref, zone) =>
          SolrQuery(
            values
              .map(_.resolve(ref, zone))
              .map { case (min, maxOpt) =>
                maxOpt
                  .map(max => created === SolrToken.fromDateRange(min, max))
                  .getOrElse(created === SolrToken.fromInstant(min))
              }
              .toList
              .foldOr
          )
        }

      case (ctx, FieldTerm.Created(Comparison.GreaterThan, values)) =>
        (ctx.currentTime, ctx.zoneId).mapN { (ref, zone) =>
          SolrQuery(
            values
              .map(_.resolve(ref, zone))
              .map { case (min, maxOpt) =>
                created > SolrToken.fromInstant(maxOpt.getOrElse(min))
              }
              .toList
              .foldOr
          )
        }

      case (ctx, FieldTerm.Created(Comparison.LowerThan, values)) =>
        (ctx.currentTime, ctx.zoneId).mapN { (ref, zone) =>
          SolrQuery(
            values
              .map(_.resolve(ref, zone))
              .map { case (min, _) =>
                created < SolrToken.fromInstant(min)
              }
              .toList
              .foldOr
          )
        }
    }

  given fieldTerm[F[_]: Monad]: SolrTokenEncoder[F, FieldTerm] =
    SolrTokenEncoder.derived[F, FieldTerm]

  given fieldSegment[F[_]: Applicative](using
      fe: SolrTokenEncoder[F, FieldTerm]
  ): SolrTokenEncoder[F, Segment.Field] =
    SolrTokenEncoder.curried[F, Segment.Field] { ctx =>
      { case Segment.Field(f) =>
        fe.encode(ctx, f)
      }
    }

  given textSegment[F[_]: Applicative]: SolrTokenEncoder[F, Segment.Text] =
    SolrTokenEncoder.basic(t => SolrQuery(SolrToken.contentAll(t.value)))

  given sortSegment[F[_]: Applicative]: SolrTokenEncoder[F, Segment.Sort] =
    SolrTokenEncoder.basic(t => SolrQuery.sort(t.value))

  given segment[F[_]](using
      et: SolrTokenEncoder[F, Segment.Text],
      ef: SolrTokenEncoder[F, Segment.Field],
      es: SolrTokenEncoder[F, Segment.Sort]
  ): SolrTokenEncoder[F, Segment] =
    SolrTokenEncoder.curried[F, Segment] { ctx =>
      {
        case s: Segment.Text  => et.encode(ctx, s)
        case s: Segment.Field => ef.encode(ctx, s)
        case s: Segment.Sort  => es.encode(ctx, s)
      }
    }

  given segmentAnd[F[_]: Monad, A](using
      se: SolrTokenEncoder[F, A]
  ): SolrTokenEncoder[F, List[A]] =
    SolrTokenEncoder.create[F, List[A]] { (ctx, nel) =>
      nel.traverse(se.encode(ctx, _)).map(_.toSeq.combineAll)
    }

  given query[F[_]: Monad](using
      se: SolrTokenEncoder[F, List[Segment]]
  ): SolrTokenEncoder[F, Query] =
    se.contramap(_.segments)
