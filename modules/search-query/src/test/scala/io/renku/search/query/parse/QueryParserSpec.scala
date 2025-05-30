package io.renku.search.query.parse

import cats.data.NonEmptyList as Nel

import io.renku.search.model.EntityType
import io.renku.search.query.*
import io.renku.search.query.Comparison.{GreaterThan, LowerThan}
import io.renku.search.query.Query.Segment
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

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
    List("createdBy", "createdby").foreach { s =>
      assertEquals(p.run(s), Field.CreatedBy)
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
      "id:id5" -> FieldTerm.IdIs(Nel.of("id5")),
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
      p.run("id:id5"),
      Query.Segment.Field(FieldTerm.IdIs(Nel.of("id5")))
    )
    assertEquals(
      p.run("foo:bar"),
      Query.Segment.Text("foo:bar")
    )
  }

  test("invalid field terms converted as text") {
    assertEquals(
      Query.parse("id:"),
      Right(Query(Segment.Text("id:")))
    )
    assertEquals(
      Query.parse("sluggy"),
      Right(Query(Segment.Text("sluggy")))
    )
    assertEquals(
      Query.parse("sorting"),
      Right(Query(Segment.Text("sorting")))
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
