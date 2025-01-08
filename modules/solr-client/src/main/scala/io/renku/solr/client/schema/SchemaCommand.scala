package io.renku.solr.client.schema

import io.renku.solr.client.schema.SchemaCommand.DeleteDynamicField

enum SchemaCommand:
  case Add(element: SchemaCommand.Element)
  case DeleteField(name: FieldName)
  case DeleteType(name: TypeName)
  case DeleteDynamicField(name: FieldName)
  case Replace(element: SchemaCommand.ReplaceElem)

  def commandName: String = this match
    case Add(_: Field)                => "add-field"
    case Add(_: FieldType)            => "add-field-type"
    case Add(_: DynamicFieldRule)     => "add-dynamic-field"
    case Add(_: CopyFieldRule)        => "add-copy-field"
    case Replace(_: Field)            => "replace-field"
    case Replace(_: FieldType)        => "replace-field-type"
    case Replace(_: DynamicFieldRule) => "replace-dynamic-field"
    case _: DeleteField               => "delete-field"
    case _: DeleteType                => "delete-field-type"
    case _: DeleteDynamicField        => "delete-dynamic-field"

object SchemaCommand:
  type Element = FieldType | Field | DynamicFieldRule | CopyFieldRule
  type ReplaceElem = FieldType | Field | DynamicFieldRule
