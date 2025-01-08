package io.renku.search.solr.documents

import io.renku.search.model.Id
import io.renku.solr.client.DocVersion

trait SolrDocument:
  def id: Id
  def version: DocVersion
  def setVersion(v: DocVersion): SolrDocument
