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

package io.renku.messages

import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import munit.FunSuite
import org.apache.avro.file.{
  CodecFactory,
  DataFileReader,
  DataFileWriter,
  SeekableByteArrayInput
}
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import scodec.bits.ByteVector

import java.io.ByteArrayOutputStream
import java.time.Instant

class SerializeDeserializeTest extends FunSuite {

  test("serialize and deserialize") {
    val data = ProjectCreated("my-project", "a description for it", None, Instant.EPOCH)
    val writer = new GenericDatumWriter[Any](ProjectCreated.SCHEMA$)

    val dw = new DataFileWriter[Any](writer)
    val baos = new ByteArrayOutputStream()
    dw.setCodec(CodecFactory.bzip2Codec())
    dw.create(ProjectCreated.SCHEMA$, baos)
    val encoded = AvroEncoder[ProjectCreated].encode(ProjectCreated.SCHEMA$).apply(data)
    dw.append(encoded)
    dw.close()

    println(s"got data: ${ByteVector.view(baos.toByteArray).toHex}")

    val reader = new GenericDatumReader[Any](ProjectCreated.SCHEMA$)
    val input = new SeekableByteArrayInput(baos.toByteArray)
    val dr = new DataFileReader[Any](input, reader)
    if (dr.hasNext) {
      val project = dr.next()
      val decoded =
        AvroDecoder[ProjectCreated].decode(ProjectCreated.SCHEMA$).apply(project)
      println(decoded)
      assertEquals(decoded, data)
    } else {
      fail("No data")
    }
    dr.close()
  }
}
