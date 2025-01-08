package io.renku.events

import io.renku.search.events.SchemaVersion
import munit.FunSuite

class SchemaVersionSpec extends FunSuite:

  test("parse values successfully"):
    val v1Values = List("v1", "V1", "1")
    v1Values.foreach { str =>
      assertEquals(SchemaVersion.fromString(str), Right(SchemaVersion.V1))
    }

    val v2Values = List("v2", "V2", "2")
    v2Values.foreach { str =>
      assertEquals(SchemaVersion.fromString(str), Right(SchemaVersion.V2))
    }
