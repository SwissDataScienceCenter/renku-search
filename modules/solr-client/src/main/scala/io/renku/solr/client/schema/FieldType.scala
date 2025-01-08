package io.renku.solr.client.schema

final case class FieldType(
    name: TypeName,
    `class`: FieldTypeClass,
    indexAnalyzer: Option[Analyzer] = None,
    queryAnalyzer: Option[Analyzer] = None,
    required: Boolean = false,
    indexed: Boolean = true,
    stored: Boolean = true,
    multiValued: Boolean = false,
    uninvertible: Boolean = false,
    docValues: Boolean = false,
    sortMissingLast: Boolean = true
):
  lazy val makeDocValue: FieldType = copy(docValues = true)
  lazy val makeMultiValued: FieldType = copy(multiValued = true)

  def withQueryAnalyzer(a: Analyzer): FieldType =
    copy(queryAnalyzer = Some(a))

  def withIndexAnalyzer(a: Analyzer): FieldType =
    copy(indexAnalyzer = Some(a))

  def withAnalyzer(a: Analyzer): FieldType =
    withQueryAnalyzer(a).withIndexAnalyzer(a)

object FieldType:

  def id(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.strField)

  def text(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.textField)

  def str(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.strField)

  def int(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.intPointField)

  def long(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.longPointField)

  def double(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.doublePointField)

  def dateTimeRange(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.dateRangeField)

  def dateTimePoint(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.datePointField)

  lazy val nestedPath: FieldType =
    FieldType(TypeName("_nest_path_"), FieldTypeClass.Defaults.nestedPath)
