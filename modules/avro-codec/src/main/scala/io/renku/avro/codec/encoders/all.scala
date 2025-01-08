package io.renku.avro.codec.encoders

trait all
    extends PrimitiveEncoders
    with StringEncoders
    with BigDecimalEncoders
    with DateTimeEncoders
    with OptionEncoders
    with EitherEncoders
    with CollectionEncoders
    with ByteArrayEncoders
    with JavaEnumEncoders
    with RecordEncoders

object all extends all
