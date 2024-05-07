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

package io.renku.solr.client.facet

import cats.Monoid

import io.bullet.borer.Encoder
import io.bullet.borer.Writer

opaque type Facets = Seq[Facet]

object Facets:
  val empty: Facets = Seq.empty
  def apply(f: Facet*): Facets = f

  extension (self: Facets)
    def isEmpty: Boolean = self.isEmpty
    def size: Int = self.size

  given Monoid[Facets] = Monoid.instance(empty, _ ++ _)
  given Encoder[Facets] = JsonEncoder.encoder

  private object JsonEncoder {
    given Encoder[Facet.ArbitraryRange] =
      new Encoder[Facet.ArbitraryRange]:
        def write(w: Writer, v: Facet.ArbitraryRange) =
          w.write(v.name)
          w.writeMapOpen(3)
          w.writeMapMember("type", "range")
          w.writeMapMember("field", v.field)
          w.writeMapMember("ranges", v.ranges.toList)
          w.writeMapClose()

    given Encoder[Facet.Terms] =
      new Encoder[Facet.Terms] {
        def write(w: Writer, t: Facet.Terms) =
          w.write(t.name)
          val size = t.productIterator.map {
            case None => 0
            case _    => 1
          }.sum
          w.writeMapOpen(size + 1)
          // facet type
          w.writeMapMember("type", "terms")
          // configuration
          w.writeMapMember("field", t.field)
          t.limit.foreach(l => w.writeMapMember("limit", l))
          t.minCount.foreach(c => w.writeMapMember("mincount", c))
          t.method.foreach(a => w.writeMapMember("method", a.name))
          w.writeMapMember("missing", t.missing)
          w.writeMapMember("numBuckets", t.numBuckets)
          w.writeMapMember("allBuckets", t.allBuckets)
          w.writeMapClose()
      }

    def encoder: Encoder[Facets] =
      new Encoder[Facets]:
        def write(w: Writer, v: Facets) =
          w.writeMapOpen(v.size)
          v.foreach {
            case f: Facet.Terms          => Encoder[Facet.Terms].write(w, f)
            case f: Facet.ArbitraryRange => Encoder[Facet.ArbitraryRange].write(w, f)
          }
          w.writeMapClose()
  }
