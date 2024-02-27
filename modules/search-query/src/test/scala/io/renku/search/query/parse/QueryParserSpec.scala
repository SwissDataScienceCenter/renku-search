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

package io.renku.search.query.parse

import cats.data.NonEmptyList as Nel
import io.renku.search.query.*
import io.renku.search.query.Query.Segment
import io.renku.search.query.Comparison.{GreaterThan, LowerThan}
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop
import io.renku.search.model.EntityType

class QueryParserSpec extends ScalaCheckSuite with ParserSuite {

  test("sort term") {
    val p = QueryParser.sortTerm
    assertEquals(p.run("sort:name-asc"), Order(SortableField.Name -> Order.Direction.Asc))
    assertEquals(
      p.run("sort:name-asc,score-desc"),
      Order(
        SortableField.Name -> Order.Direction.Asc,
        SortableField.Score -> Order.Direction.Desc
      )
    )
  }

  test("string list") {
    val p = QueryParser.values
    assertEquals(p.run("a,b,c"), nel("a", "b", "c"))
    assertEquals(p.run("a, b, c"), nel("a", "b", "c"))
    assertEquals(p.run("a"), nel("a"))
    assertEquals(p.run("""a,"b",c"""), nel("a", "b", "c"))
    assertEquals(p.run("""a,"x\"c",c"""), nel("a", """x"c""", "c"))
    assertEquals(p.run("\"hello world\""), nel("hello world"))
  }

  test("field name") {
    val p = QueryParser.fieldNameFrom(Field.values.toSet)
    List("projectId", "projectid").foreach { s =>
      assertEquals(p.run(s), Field.ProjectId)
    }
    Field.values.foreach { f =>
      assertEquals(p.run(f.name), f)
    }
  }

  test("field term: created") {
    val p = QueryParser.fieldTerm
    val pd = DateTimeRef(DateTimeParser.partialDateTime.run("2023-05"))
    assertEquals(
      p.run("created:2023-05"),
      FieldTerm.Created(Comparison.Is, Nel.of(pd))
    )

    assertEquals(
      p.run("created<2023-05"),
      FieldTerm.Created(LowerThan, Nel.of(pd))
    )
    assertEquals(
      p.run("created>2023-05"),
      FieldTerm.Created(GreaterThan, Nel.of(pd))
    )
  }

  test("field term") {
    val p = QueryParser.fieldTerm
    val data = List(
      "projectId:id5" -> FieldTerm.ProjectIdIs(Nel.of("id5")),
      "name:\"my project\"" -> FieldTerm.NameIs(Nel.of("my project")),
      "slug:ab1,ab2" -> FieldTerm.SlugIs(Nel.of("ab1", "ab2")),
      "type:project" -> FieldTerm.TypeIs(Nel.of(EntityType.Project))
    )
    data.foreach { case (in, expect) =>
      assertEquals(p.run(in), expect)
    }
  }

  test("segment") {
    val p = QueryParser.segment
    assertEquals(
      p.run("hello"),
      Query.Segment.Text("hello")
    )
    assertEquals(
      p.run("projectId:id5"),
      Query.Segment.Field(FieldTerm.ProjectIdIs(Nel.of("id5")))
    )
    assertEquals(
      p.run("foo:bar"),
      Query.Segment.Text("foo:bar")
    )
  }

  test("invalid field terms converted as text".ignore) {
    assertEquals(
      Query.parse("projectId:"),
      Right(Query(Segment.Text("projectId:")))
    )
    assertEquals(
      Query.parse("projectId1"),
      Right(Query(Segment.Text("projectId1")))
    )
  }

  property("generated queries") {
    Prop.forAll(QueryGenerators.query) { q =>
      val qStr = q.render
      val parsed = Query.parse(qStr).fold(sys.error, identity)
      if (q != parsed) {
        // this is for better error messages when things fail
        println(qStr)
        assertEquals(q, parsed)
      }
      parsed == q
    }
  }
}
