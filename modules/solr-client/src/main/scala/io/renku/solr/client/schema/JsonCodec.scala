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

package io.renku.solr.client.schema

import cats.kernel.Monoid
import cats.syntax.all.*
import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.avro.codec.encoders.StringEncoders
import io.renku.avro.codec.json.AvroJsonEncoder
import io.renku.solr.client.schema.Analyzer.AnalyzerType
import io.renku.solr.client.schema.SchemaCommand.Add
import org.apache.avro
import org.apache.avro.SchemaBuilder
import scodec.bits.ByteVector

trait JsonCodec {

  private val fieldSchema =
    // format: off
    SchemaBuilder.record("Field").fields()
      .name("name").`type`("string").noDefault()
      .name("type").`type`("string").noDefault()
      .name("required").`type`("boolean").noDefault()
      .name("indexed").`type`("boolean").noDefault()
      .name("stored").`type`("boolean").noDefault()
      .name("multiValued").`type`("boolean").noDefault()
      .name("uninvertible").`type`("boolean").noDefault()
      .name("docValues").`type`("boolean").noDefault()
      .endRecord()
    //format: on

  given AvroJsonEncoder[Field] = AvroJsonEncoder.create(fieldSchema)

  private val dynamicFieldRuleSchema =
    // format: off
    SchemaBuilder.record("Field").fields()
      .name("name").`type`("string").noDefault()
      .name("type").`type`("string").noDefault()
      .name("required").`type`("boolean").noDefault()
      .name("indexed").`type`("boolean").noDefault()
      .name("stored").`type`("boolean").noDefault()
      .name("multiValued").`type`("boolean").noDefault()
      .name("uninvertible").`type`("boolean").noDefault()
      .name("docValues").`type`("boolean").noDefault()
      .endRecord()
  //format: on

  given AvroJsonEncoder[DynamicFieldRule] = AvroJsonEncoder.create(dynamicFieldRuleSchema)

  private val copyFieldRuleSchema =  //format: off
    SchemaBuilder.record("CopyFieldRule").fields()
      .name("source").`type`("string").noDefault()
      .name("dest").`type`("string").noDefault()
      .name("maxChars").`type`(SchemaBuilder.builder().nullable().`type`("int")).noDefault()
      .endRecord()
  //format: on
  given AvroJsonEncoder[CopyFieldRule] = AvroJsonEncoder.create(copyFieldRuleSchema)

  given AvroEncoder[Analyzer.AnalyzerType] = StringEncoders.StringEncoder.contramap {
    case AnalyzerType.Index     => "index"
    case AnalyzerType.Multiterm => "multiterm"
    case AnalyzerType.Query     => "query"
    case AnalyzerType.None      => ""
  }
  given AvroEncoder[Filter] = AvroEncoder.derived[Filter]
  given AvroEncoder[Tokenizer] = AvroEncoder.derived[Tokenizer]
  given AvroEncoder[Analyzer] = AvroEncoder.derived[Analyzer]

  //format: off
  private val fieldTypeSchema =
    SchemaBuilder.record("FieldType").fields()
      .name("name").`type`("string").noDefault()
      .name("class").`type`("string").noDefault()
      .name("required").`type`("boolean").noDefault()
      .name("indexed").`type`("boolean").noDefault()
      .name("stored").`type`("boolean").noDefault()
      .name("multiValued").`type`("boolean").noDefault()
      .name("uninvertible").`type`("boolean").noDefault()
      .name("docValues").`type`("boolean").noDefault()
      .endRecord()
  //format: on

  given AvroJsonEncoder[FieldType] = AvroJsonEncoder.create(fieldTypeSchema)

  given AvroJsonEncoder[SchemaCommand.Element] = AvroJsonEncoder {
    case v: Field            => AvroJsonEncoder[Field].encode(v)
    case v: FieldType        => AvroJsonEncoder[FieldType].encode(v)
    case v: CopyFieldRule    => AvroJsonEncoder[CopyFieldRule].encode(v)
    case v: DynamicFieldRule => AvroJsonEncoder[DynamicFieldRule].encode(v)
  }

  private given AvroJsonEncoder[SchemaCommand.Add] = AvroJsonEncoder {
    case SchemaCommand.Add(v: Field) =>
      ByteVector.view(""""add-field": """.getBytes) ++
        AvroJsonEncoder[Field].encode(v)

    case SchemaCommand.Add(v: FieldType) =>
      ByteVector.view(""""add-field-type": """.getBytes) ++
        AvroJsonEncoder[FieldType].encode(v)

    case SchemaCommand.Add(v: CopyFieldRule) =>
      ByteVector.view(""""add-copy-field": """.getBytes) ++
        AvroJsonEncoder[CopyFieldRule].encode(v)

    case SchemaCommand.Add(v: DynamicFieldRule) =>
      ByteVector.view(""""add-dynamic-field": """.getBytes) ++
        AvroJsonEncoder[DynamicFieldRule].encode(v)
  }

  private given AvroJsonEncoder[SchemaCommand.DeleteField] = AvroJsonEncoder {
    case SchemaCommand.DeleteField(v) =>
      ByteVector.view(s""""delete-field": {"name": "${v.name}"}""".getBytes)
  }

  private given AvroJsonEncoder[SchemaCommand.DeleteType] = AvroJsonEncoder {
    case SchemaCommand.DeleteType(v) =>
      ByteVector.view(s""""delete-field-type": {"name": "${v.name}"} """.getBytes)
  }

  private given AvroJsonEncoder[SchemaCommand.DeleteDynamicField] = AvroJsonEncoder {
    case SchemaCommand.DeleteDynamicField(v) =>
      ByteVector.view(s""""delete-dynamic-field": {"name": "${v.name}"} """.getBytes)
  }

  private given AvroJsonEncoder[SchemaCommand] = AvroJsonEncoder {
    case c: SchemaCommand.Add => AvroJsonEncoder[SchemaCommand.Add].encode(c)
    case c: SchemaCommand.DeleteField =>
      AvroJsonEncoder[SchemaCommand.DeleteField].encode(c)
    case c: SchemaCommand.DeleteType =>
      AvroJsonEncoder[SchemaCommand.DeleteType].encode(c)
    case c: SchemaCommand.DeleteDynamicField =>
      AvroJsonEncoder[SchemaCommand.DeleteDynamicField].encode(c)
    case SchemaCommand.Raw(c) =>
      ByteVector.view(c.getBytes)
  }

  given Monoid[ByteVector] = Monoid.instance(ByteVector.empty, _ ++ _)

  given AvroJsonEncoder[Seq[SchemaCommand]] = AvroJsonEncoder { seq =>
    seq
      .map(AvroJsonEncoder[SchemaCommand].encode)
      .foldSmash(
        ByteVector.fromByte('{'),
        ByteVector.fromByte(','),
        ByteVector.fromByte('}')
      )
  }
}

object JsonCodec extends JsonCodec
