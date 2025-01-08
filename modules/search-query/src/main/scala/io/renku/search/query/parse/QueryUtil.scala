package io.renku.search.query.parse

import cats.syntax.all.*

import io.renku.search.query.Query
import io.renku.search.query.Query.Segment

private[query] object QueryUtil {

  def collapse(q: Query): Query =
    Query(collapseTextSegments(q.segments))

  private def collapseTextSegments(segs: List[Segment]): List[Segment] = {
    @annotation.tailrec
    def loop(
        in: List[Segment],
        curr: Option[Segment.Text],
        result: List[Segment]
    ): List[Segment] =
      in match
        case first :: rest =>
          (first, curr) match
            case (t1: Segment.Text, tc) =>
              loop(rest, tc |+| Some(t1), result)

            case t @ ((_: Segment.Field) | (_: Segment.Sort), Some(tc)) =>
              loop(rest, None, t._1 :: tc :: result)

            case t @ ((_: Segment.Field) | (_: Segment.Sort), None) =>
              loop(rest, None, t._1 :: result)

        case Nil => (curr.toList ::: result).reverse

    loop(segs, None, Nil)
  }
}
