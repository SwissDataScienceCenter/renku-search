package io.renku.solr.client.schema

final case class CoreSchema(
    name: String,
    version: Double,
    uniqueKey: FieldName,
    fieldTypes: List[FieldType] = Nil,
    fields: List[Field] = Nil,
    dynamicFields: List[DynamicFieldRule] = Nil,
    copyFields: List[CopyFieldRule] = Nil
)
