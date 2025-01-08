package io.renku.solr.client

import cats.kernel.Monoid

import io.bullet.borer.Encoder
import io.renku.solr.client.schema.FieldName

opaque type SolrSort = Seq[(FieldName, SolrSort.Direction)]

object SolrSort:
  enum Direction:
    case Asc
    case Desc
    val name: String = productPrefix.toLowerCase

  object Direction:
    def fromString(s: String): Either[String, Direction] =
      Direction.values
        .find(_.toString.equalsIgnoreCase(s))
        .toRight(s"Invalid sort direction: $s")
    def unsafeFromString(s: String): Direction =
      fromString(s).fold(sys.error, identity)

    given Encoder[Direction] = Encoder.forString.contramap(_.name)

  def apply(s: (FieldName, Direction)*): SolrSort = s

  val empty: SolrSort = Seq.empty

  extension (self: SolrSort)
    def isEmpty: Boolean = self.isEmpty
    def nonEmpty: Boolean = !self.isEmpty
    def ++(next: SolrSort): SolrSort =
      Monoid[SolrSort].combine(self, next)

    def +(n: (FieldName, Direction)): SolrSort =
      self ++ Seq(n)

    private[client] def toSolr: String =
      self.map { case (f, d) => s"${f.name} ${d.name}" }.mkString(",")

  given Monoid[SolrSort] =
    Monoid.instance(empty, (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else a ++ b)

  given Encoder[SolrSort] = Encoder.forString.contramap(_.toSolr)
