package io.renku.solr.client.schema

final case class DynamicFieldRule(
    name: FieldName,
    `type`: TypeName,
    required: Boolean = false,
    indexed: Boolean = true,
    stored: Boolean = true,
    multiValued: Boolean = false,
    uninvertible: Boolean = false,
    docValues: Boolean = false
)
