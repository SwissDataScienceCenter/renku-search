package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData

trait JavaEnumEncoders {

  given [E <: Enum[E]]: AvroEncoder[E] =
    AvroEncoder.curried { schema => e =>
      require(
        schema.getType == Schema.Type.ENUM,
        s"schema is not an enum: $schema (${schema.getType})"
      )
      GenericData.get().createEnum(e.name(), schema)
    }
}
