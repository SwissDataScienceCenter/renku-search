package io.renku.search.solr.schema

import io.renku.solr.client.migration.SchemaMigration

object Migrations {

  val all: Seq[SchemaMigration] = Seq(
    SchemaMigration(version = 1L, EntityDocumentSchema.initialEntityDocumentAdd),
    SchemaMigration(version = 2L, EntityDocumentSchema.copyContentField),
    SchemaMigration(version = 3L, EntityDocumentSchema.userFields),
    SchemaMigration(version = 4L, EntityDocumentSchema.projectMembersFields),
    SchemaMigration(version = 5L, EntityDocumentSchema.keywordField),
    SchemaMigration(version = 6L, EntityDocumentSchema.namespaceField),
    SchemaMigration(version = 7L, EntityDocumentSchema.editorAndViewerRoles),
    SchemaMigration(version = 8L, EntityDocumentSchema.groupRoles),
    SchemaMigration(version = 9L, EntityDocumentSchema.replaceTextTypes)
  )
}
