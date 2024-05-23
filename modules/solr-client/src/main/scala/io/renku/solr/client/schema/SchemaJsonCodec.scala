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

import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.{Decoder, Encoder, Writer}
import io.renku.solr.client.schema.SchemaCommand.Element

trait SchemaJsonCodec {

  given Encoder[Tokenizer] = MapBasedCodecs.deriveEncoder
  given Decoder[Tokenizer] = MapBasedCodecs.deriveDecoder

  given Encoder[Filter] = MapBasedCodecs.deriveEncoder
  given Decoder[Filter] = MapBasedCodecs.deriveDecoder

  given Encoder[Analyzer.AnalyzerType] =
    Encoder.forString.contramap(_.productPrefix.toLowerCase)
  given Decoder[Analyzer.AnalyzerType] =
    Decoder.forString.mapEither(Analyzer.AnalyzerType.fromString)

  given Encoder[Analyzer] = MapBasedCodecs.deriveEncoder
  given Decoder[Analyzer] = MapBasedCodecs.deriveDecoder

  given Encoder[FieldType] = MapBasedCodecs.deriveEncoder
  given Decoder[FieldType] = MapBasedCodecs.deriveDecoder

  given Encoder[DynamicFieldRule] = MapBasedCodecs.deriveEncoder
  given Decoder[DynamicFieldRule] = MapBasedCodecs.deriveDecoder

  given Encoder[CopyFieldRule] = MapBasedCodecs.deriveEncoder
  given Decoder[CopyFieldRule] = MapBasedCodecs.deriveDecoder

  given Decoder[CoreSchema] = MapBasedCodecs.deriveDecoder
  given Encoder[CoreSchema] = MapBasedCodecs.deriveEncoder

  given (using
      e1: Encoder[Field],
      e2: Encoder[FieldType],
      e3: Encoder[DynamicFieldRule],
      e4: Encoder[CopyFieldRule]
  ): Encoder[SchemaCommand.Element] =
    (w: Writer, value: Element) =>
      value match
        case v: Field            => e1.write(w, v)
        case v: FieldType        => e2.write(w, v)
        case v: DynamicFieldRule => e3.write(w, v)
        case v: CopyFieldRule    => e4.write(w, v)

  private def commandPayloadEncoder(using
      e: Encoder[SchemaCommand.Element]
  ): Encoder[SchemaCommand] =
    new Encoder[SchemaCommand]:
      override def write(w: Writer, value: SchemaCommand) =
        value match
          case SchemaCommand.Add(v) =>
            e.write(w, v)
          case SchemaCommand.DeleteType(tn) =>
            w.writeMap(Map("name" -> tn))
          case SchemaCommand.DeleteField(fn) =>
            w.writeMap(Map("name" -> fn))
          case SchemaCommand.DeleteDynamicField(fn) =>
            w.writeMap(Map("name" -> fn))

  given Encoder[Seq[SchemaCommand]] =
    new Encoder[Seq[SchemaCommand]]:
      override def write(w: Writer, value: Seq[SchemaCommand]) =
        w.writeMapOpen(value.size)
        value.foreach { v =>
          w.writeMapMember(v.commandName, v)(using
            Encoder[String],
            commandPayloadEncoder
          )
        }
        w.writeMapClose()

  given Encoder[SchemaCommand] =
    Encoder[Seq[SchemaCommand]].contramap(Seq(_))
}

object SchemaJsonCodec extends SchemaJsonCodec
