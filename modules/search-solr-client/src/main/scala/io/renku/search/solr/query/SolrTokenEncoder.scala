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

import cats.Applicative
import cats.syntax.all.*
import cats.effect.kernel.Sync
import scala.deriving.*
import scala.collection.AbstractIterable

trait SolrTokenEncoder[F[_], A]:
  def encode(ctx: Context[F], value: A): F[SolrToken]
  final def contramap[B](f: B => A): SolrTokenEncoder[F, B] =
    SolrTokenEncoder.create((ctx, b) => encode(ctx, f(b)))

object SolrTokenEncoder:
  def apply[F[_], A](using e: SolrTokenEncoder[F, A]): SolrTokenEncoder[F, A] = e

  def create[F[_], A](
      f: (ctx: Context[F], value: A) => F[SolrToken]
  ): SolrTokenEncoder[F, A] =
    new SolrTokenEncoder[F, A]:
      def encode(ctx: Context[F], value: A) = f(ctx, value)

  def curried[F[_], A](f: Context[F] => A => F[SolrToken]): SolrTokenEncoder[F, A] =
    create[F, A]((ctx, v) => f(ctx)(v))

  inline def derived[F[_]: Sync, A](using Mirror.Of[A]): SolrTokenEncoder[F, A] =
    Macros.derived[F, A]

  def basic[F[_]: Applicative, A](f: A => SolrToken): SolrTokenEncoder[F, A] =
    create[F, A]((_, v) => f(v).pure[F])

  private object Macros {
    import scala.compiletime.*

    inline def derived[F[_]: Sync, T](using m: Mirror.Of[T]): SolrTokenEncoder[F, T] =
      lazy val elemInstances = summonInstances[F, T, m.MirroredElemTypes]
      inline m match
        case s: Mirror.SumOf[T]     => sumTokenEncoder(s, elemInstances)
        case p: Mirror.ProductOf[T] => prodTokenEncoder(p, elemInstances)

    inline def summonInstances[F[_]: Sync, T, Elems <: Tuple]
        : List[SolrTokenEncoder[F, ?]] =
      inline erasedValue[Elems] match
        case _: (elem *: elems) =>
          deriveOrSummon[F, T, elem] :: summonInstances[F, T, elems]
        case _: EmptyTuple => Nil

    inline def deriveOrSummon[F[_]: Sync, T, Elem]: SolrTokenEncoder[F, Elem] =
      inline erasedValue[Elem] match
//        case _: T => deriveRec[F, T, Elem]
        case _ => summonInline[SolrTokenEncoder[F, Elem]]

    /// we don't need recursive derivation right now
    // inline def deriveRec[F[_]:Sync, T, Elem]: SolrTokenEncoder[F, Elem] =
    //   inline erasedValue[T] match
    //     case _: Elem => error("infinite recursive derivation")
    //     case _       => Macros.derived[F, Elem](using Sync[F], summonInline[Mirror.Of[Elem]]) // recursive derivation

    def sumTokenEncoder[F[_]: Sync, T](
        s: Mirror.SumOf[T],
        elems: => List[SolrTokenEncoder[F, ?]]
    ): SolrTokenEncoder[F, T] =
      SolrTokenEncoder.create[F, T] { (ctx, v) =>
        val ord = s.ordinal(v)
        elems(ord).asInstanceOf[SolrTokenEncoder[F, Any]].encode(ctx, v)
      }

    def prodTokenEncoder[F[_]: Sync, T](
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
        vel.map(_.foldAnd)
      }

    def iterable[T](p: T): Iterable[Any] = new AbstractIterable[Any]:
      def iterator: Iterator[Any] = p.asInstanceOf[Product].productIterator
  }
