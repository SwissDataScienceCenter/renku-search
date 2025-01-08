package io.renku.events

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.{AvroDecoder, AvroEncoder, AvroIO}
import io.renku.events.v1.{ProjectCreated, Visibility}
import munit.FunSuite

class SerializeDeserializeSpec extends FunSuite {

  test("serialize and deserialize ProjectCreated") {
    val data = ProjectCreated(
      UUID.randomUUID().toString,
      "my-project",
      "slug",
      Seq.empty,
      Visibility.PUBLIC,
      Some("a description for it"),
      Seq("data", "science"),
      "created-by-me",
      Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )
    val avro = AvroIO(ProjectCreated.SCHEMA$)

    val bytes = avro.write(Seq(data))
    val decoded = avro.read[ProjectCreated](bytes)

    assertEquals(decoded, List(data))
  }

}
