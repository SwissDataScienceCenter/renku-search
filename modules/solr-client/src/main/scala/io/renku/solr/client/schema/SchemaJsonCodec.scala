package io.renku.solr.client.schema

import io.bullet.borer.NullOptions.given
import io.bullet.borer.Reader
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.{Decoder, Encoder, Writer}
import io.renku.solr.client.schema.SchemaCommand.Element

trait SchemaJsonCodec {

  given Encoder[Tokenizer] = MapBasedCodecs.deriveEncoder
  given Decoder[Tokenizer] = MapBasedCodecs.deriveDecoder

  given Encoder[Filter] = { (w: Writer, value: Filter) =>
    w.writeMapStart()
    w.writeMapMember("name", value.name)
    value.settings match {
      case None => ()
      case Some(s) =>
        s.asMap.foreach { case (k, v) =>
          w.writeMapMember(k, v)
        }
    }
    w.writeMapClose()
  }

  given Decoder[Filter] = Decoder.forMap[String, String].mapOption { data =>
    data.get("name").map { name =>
      Filter(name, Filter.Settings.createFromMap(data.removed("name")))
    }
  }

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
          case SchemaCommand.Replace(v) =>
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
