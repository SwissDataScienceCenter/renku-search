package io.renku.avro.codec.decoders

trait all
    extends PrimitiveDecoders
    with StringDecoders
    with BigDecimalDecoders
    with DateTimeDecoders
    with OptionDecoders
    with CollectionDecoders
    with ByteArrayDecoders
    with JavaEnumDecoders
    with EitherDecoders
    with RecordDecoders

object all extends all
