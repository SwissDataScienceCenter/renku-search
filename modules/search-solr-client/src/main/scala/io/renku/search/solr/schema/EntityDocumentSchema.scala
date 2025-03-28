package io.renku.search.solr.schema

import io.renku.solr.client.schema.*

object EntityDocumentSchema:

  object Fields:
    val createdBy: FieldName = FieldName("createdBy")
    val creationDate: FieldName = FieldName("creationDate")
    val description: FieldName = FieldName("description")
    val entityType: FieldName = FieldName("_type")
    val kind: FieldName = FieldName("_kind")
    val firstName: FieldName = FieldName("firstName")
    val id: FieldName = FieldName("id")
    val lastName: FieldName = FieldName("lastName")
    val members: FieldName = FieldName("members")
    val name: FieldName = FieldName("name")
    val nestPath: FieldName = FieldName("_nest_path_")
    val owners: FieldName = FieldName("owners")
    val editors: FieldName = FieldName("editors")
    val viewers: FieldName = FieldName("viewers")
    val groupOwners: FieldName = FieldName("groupOwners")
    val groupEditors: FieldName = FieldName("groupEditors")
    val groupViewers: FieldName = FieldName("groupViewers")

    // catch-all members field
    val membersAll: FieldName = FieldName("members_all")
    val repositories: FieldName = FieldName("repositories")
    val slug: FieldName = FieldName("slug")
    val visibility: FieldName = FieldName("visibility")
    val keywords: FieldName = FieldName("keywords")
    val namespace: FieldName = FieldName("namespace")

    val root: FieldName = FieldName("_root_")
    val nestParent: FieldName = FieldName("_nest_parent_")
    // catch-all field
    val contentAll: FieldName = FieldName("content_all")
    // virtual score field
    val score: FieldName = FieldName("score")

  private object Analyzers {
    val textIndex = Analyzer(
      tokenizer = Tokenizer.uax29UrlEmail,
      filters = Seq(
        Filter.lowercase,
        Filter.stop,
        Filter.englishMinimalStem,
        Filter.asciiFolding,
        Filter.edgeNGram(Filter.EdgeNGramSettings(2, 8, true))
      )
    )
    val textQuery = Analyzer(
      tokenizer = Tokenizer.uax29UrlEmail,
      filters = Seq(
        Filter.lowercase,
        Filter.stop,
        Filter.englishMinimalStem,
        Filter.asciiFolding
      )
    )
  }

  object FieldTypes:
    val id: FieldType = FieldType.id(TypeName("SearchId")).makeDocValue
    val string: FieldType = FieldType.str(TypeName("SearchString")).makeDocValue
    val text: FieldType =
      FieldType
        .text(TypeName("SearchText"))
        .withIndexAnalyzer(Analyzers.textIndex)
        .withQueryAnalyzer(Analyzers.textQuery)
    val textAll: FieldType =
      FieldType
        .text(TypeName("SearchTextAll"))
        .withIndexAnalyzer(Analyzers.textIndex)
        .withQueryAnalyzer(Analyzers.textQuery)
        .makeMultiValued
    val dateTime: FieldType = FieldType.dateTimePoint(TypeName("SearchDateTime"))

  val initialEntityDocumentAdd: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(FieldTypes.id),
    SchemaCommand.Add(FieldTypes.string),
    SchemaCommand.Add(FieldTypes.text),
    SchemaCommand.Add(FieldTypes.dateTime),
    SchemaCommand.Add(Field(Fields.entityType, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.kind, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.name, FieldTypes.text)),
    SchemaCommand.Add(Field(Fields.slug, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.repositories, FieldTypes.string).makeMultiValued),
    SchemaCommand.Add(Field(Fields.visibility, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.description, FieldTypes.text)),
    SchemaCommand.Add(Field(Fields.createdBy, FieldTypes.id)),
    SchemaCommand.Add(Field(Fields.creationDate, FieldTypes.dateTime)),
    SchemaCommand.Add(Field(Fields.nestParent, FieldTypes.string))
  )

  val copyContentField: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(FieldTypes.textAll),
    SchemaCommand.Add(Field(Fields.contentAll, FieldTypes.textAll).makeMultiValued),
    SchemaCommand.Add(CopyFieldRule(Fields.name, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.description, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.slug, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.repositories, Fields.contentAll))
  )

  val userFields: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.firstName, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.lastName, FieldTypes.string)),
    SchemaCommand.Add(CopyFieldRule(Fields.firstName, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.lastName, Fields.contentAll))
  )

  val projectMembersFields: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.owners, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(Field(Fields.members, FieldTypes.id).makeMultiValued)
  )

  val keywordField: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.keywords, FieldTypes.string).makeMultiValued),
    SchemaCommand.Add(CopyFieldRule(Fields.keywords, Fields.contentAll))
  )

  val namespaceField: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.namespace, FieldTypes.string)),
    SchemaCommand.Add(CopyFieldRule(Fields.namespace, Fields.contentAll))
  )

  val editorAndViewerRoles: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.editors, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(Field(Fields.viewers, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(Field(Fields.membersAll, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(CopyFieldRule(Fields.owners, Fields.membersAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.editors, Fields.membersAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.viewers, Fields.membersAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.members, Fields.membersAll))
  )

  val groupRoles: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(Field(Fields.groupEditors, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(Field(Fields.groupViewers, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(Field(Fields.groupOwners, FieldTypes.id).makeMultiValued),
    SchemaCommand.Add(CopyFieldRule(Fields.groupOwners, Fields.membersAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.groupEditors, Fields.membersAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.groupViewers, Fields.membersAll))
  )

  val replaceTextTypes: Seq[SchemaCommand] = Seq(
    SchemaCommand.Replace(FieldTypes.text),
    SchemaCommand.Replace(FieldTypes.textAll)
  )
