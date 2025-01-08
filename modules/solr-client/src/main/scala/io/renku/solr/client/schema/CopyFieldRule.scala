package io.renku.solr.client.schema

final case class CopyFieldRule(
    source: FieldName,
    dest: FieldName,
    maxChars: Option[Int] = None
)
