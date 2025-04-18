package io.renku.avro.codec.encoders

import io.renku.avro.codec.{AvroCodecException, AvroEncoder}
import org.apache.avro.LogicalTypes.Decimal
import org.apache.avro.{Conversions, Schema}

import java.math.RoundingMode

trait BigDecimalEncoders:
  given AvroEncoder[BigDecimal] = (schema: Schema) =>
    schema.getType match
      case Schema.Type.BYTES  => BigDecimalEncoders.ForBytes.encode(schema)
      case Schema.Type.STRING => BigDecimalEncoders.ForString.encode(schema)
      case Schema.Type.FIXED  => BigDecimalEncoders.ForFixed.encode(schema)
      case t =>
        throw AvroCodecException.encode(
          s"Unable to create AvroEncoder with schema type $t for BigDecimal"
        )

object BigDecimalEncoders:
  object ForBytes extends AvroEncoder[BigDecimal]:
    override def encode(schema: Schema): BigDecimal => Any = {
      require(schema.getType == Schema.Type.BYTES)

      val logical = schema.getLogicalType.asInstanceOf[Decimal]
      val convert = new Conversions.DecimalConversion()
      val rounding = RoundingMode.HALF_UP
      { v =>
        convert.toBytes(
          v.underlying().setScale(logical.getScale, rounding),
          schema,
          logical
        )
      }
    }

  object ForString extends AvroEncoder[BigDecimal] with StringEncoders:
    override def encode(schema: Schema): BigDecimal => Any = {
      require(schema.getType == Schema.Type.STRING)

      AvroEncoder[String].contramap[BigDecimal](_.toString).encode(schema)
    }

  object ForFixed extends AvroEncoder[BigDecimal]:
    def encode(schema: Schema): BigDecimal => Any = {
      require(schema.getType == Schema.Type.FIXED)

      val logical = schema.getLogicalType.asInstanceOf[Decimal]
      val convert = new Conversions.DecimalConversion()
      val rounding = RoundingMode.HALF_UP
      { v =>
        convert.toFixed(
          v.underlying().setScale(logical.getScale, rounding),
          schema,
          logical
        )
      }
    }
