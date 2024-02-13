package io.renku.search.query

import io.bullet.borer.Json
import io.renku.search.query.Query.Segment
import munit.FunSuite

import java.time.Instant

class QueryJsonSpec extends FunSuite {

  test("playing") {
    println(Query.empty.asString)
    val q = Query(
      Segment.projectIdIs("p1"),
      Segment.text("foo bar"),
      Segment.nameIs("ai-project-15048"),
      Segment.creationDateLower(Instant.now())
    )
    println(q.asString)
    val jsonStr = Json.encode(q).toUtf8String
    println(jsonStr)
    val decoded = Json.decode(jsonStr.getBytes).to[Query].value
    println(decoded)
    assertEquals(decoded, q)

    val q2 = Query(Segment.projectIdIs("id-2"), Segment.projectIdIs("id-3"))
    val q2Json = Json.encode(q2).toUtf8String
    assertEquals(q2Json, """{"ProjectId":"id-2","ProjectId":"id-3"}""")
    val decodedQ2 = Json.decode(q2Json.getBytes).to[Query].value
    println(decodedQ2)
  }
}
