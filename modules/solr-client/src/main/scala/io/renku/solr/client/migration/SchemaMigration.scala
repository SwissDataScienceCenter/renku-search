package io.renku.solr.client.migration

import io.renku.solr.client.schema.*

final case class SchemaMigration(
    version: Long,
    commands: Seq[SchemaCommand],
    requiresReIndex: Boolean = false
):

  def withRequiresReIndex: SchemaMigration =
    copy(requiresReIndex = true)

  def alignWith(schema: CoreSchema): SchemaMigration =
    copy(commands = commands.filterNot(SchemaMigration.isApplied(schema)))

object SchemaMigration:
  def apply(version: Long, cmd: SchemaCommand, more: SchemaCommand*): SchemaMigration =
    SchemaMigration(version, cmd +: more)

  def isApplied(schema: CoreSchema)(cmd: SchemaCommand): Boolean = cmd match
    case SchemaCommand.Add(ft: FieldType) =>
      schema.fieldTypes.exists(_.name == ft.name)
    case SchemaCommand.Add(f: Field) =>
      schema.fields.exists(_.name == f.name)
    case SchemaCommand.Add(r: DynamicFieldRule) =>
      schema.dynamicFields.exists(_.name == r.name)
    case SchemaCommand.Add(r: CopyFieldRule) =>
      schema.copyFields.exists(cf => cf.source == r.source && cf.dest == r.dest)
    case SchemaCommand.DeleteField(name) =>
      schema.fields.forall(_.name != name)
    case SchemaCommand.DeleteType(name) =>
      schema.fieldTypes.forall(_.name != name)
    case SchemaCommand.DeleteDynamicField(name) =>
      schema.dynamicFields.forall(_.name != name)
    case SchemaCommand.Replace(ft: FieldType) =>
      schema.fieldTypes.exists(_ == ft)
    case SchemaCommand.Replace(f: Field) =>
      schema.fields.exists(_ == f)
    case SchemaCommand.Replace(r: DynamicFieldRule) =>
      schema.dynamicFields.exists(_ == r)
