package io.renku.search.solr.query

import scala.collection.AbstractIterable
import scala.deriving.*

import cats.Functor
import cats.syntax.all.*
import cats.{Applicative, Monad}

trait SolrTokenEncoder[F[_], A]:
  def encode(ctx: Context[F], value: A): F[SolrQuery]
  final def contramap[B](f: B => A): SolrTokenEncoder[F, B] =
    SolrTokenEncoder.create((ctx, b) => encode(ctx, f(b)))
  final def modify(f: SolrQuery => SolrQuery)(using Functor[F]): SolrTokenEncoder[F, A] =
    SolrTokenEncoder.create((ctx, a) => encode(ctx, a).map(f))

object SolrTokenEncoder:
  def apply[F[_], A](using e: SolrTokenEncoder[F, A]): SolrTokenEncoder[F, A] = e

  def create[F[_], A](
      f: (ctx: Context[F], value: A) => F[SolrQuery]
  ): SolrTokenEncoder[F, A] =
    new SolrTokenEncoder[F, A]:
      def encode(ctx: Context[F], value: A) = f(ctx, value)

  def curried[F[_], A](f: Context[F] => A => F[SolrQuery]): SolrTokenEncoder[F, A] =
    create[F, A]((ctx, v) => f(ctx)(v))

  inline def derived[F[_]: Monad, A](using Mirror.Of[A]): SolrTokenEncoder[F, A] =
    Macros.derived[F, A]

  def basic[F[_]: Applicative, A](f: A => SolrQuery): SolrTokenEncoder[F, A] =
    create[F, A]((_, v) => f(v).pure[F])

  private object Macros {
    import scala.compiletime.*

    inline def derived[F[_]: Monad, T](using m: Mirror.Of[T]): SolrTokenEncoder[F, T] =
      lazy val elemInstances = summonInstances[F, T, m.MirroredElemTypes]
      inline m match
        case s: Mirror.SumOf[T]     => sumTokenEncoder(s, elemInstances)
        case p: Mirror.ProductOf[T] => prodTokenEncoder(p, elemInstances)

    inline def summonInstances[F[_]: Monad, T, Elems <: Tuple]
        : List[SolrTokenEncoder[F, ?]] =
      inline erasedValue[Elems] match
        case _: (elem *: elems) =>
          summonInline[SolrTokenEncoder[F, elem]] :: summonInstances[F, T, elems]
        case _: EmptyTuple => Nil

    def sumTokenEncoder[F[_]: Monad, T](
        s: Mirror.SumOf[T],
        elems: => List[SolrTokenEncoder[F, ?]]
    ): SolrTokenEncoder[F, T] =
      SolrTokenEncoder.create[F, T] { (ctx, v) =>
        val ord = s.ordinal(v)
        elems(ord).asInstanceOf[SolrTokenEncoder[F, Any]].encode(ctx, v)
      }

    def prodTokenEncoder[F[_]: Monad, T](
        s: Mirror.ProductOf[T],
        elems: => List[SolrTokenEncoder[F, ?]]
    ): SolrTokenEncoder[F, T] =
      SolrTokenEncoder.create[F, T] { (ctx, v) =>
        val vel = iterable(v)
          .zip(elems)
          .map { case (va, ea) =>
            ea.asInstanceOf[SolrTokenEncoder[F, Any]].encode(ctx, va)
          }
          .toList
          .sequence
        vel.map(_.combineAll)
      }

    def iterable[T](p: T): Iterable[Any] = new AbstractIterable[Any]:
      def iterator: Iterator[Any] = p.asInstanceOf[Product].productIterator
  }
