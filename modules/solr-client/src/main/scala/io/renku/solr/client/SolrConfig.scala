package io.renku.solr.client

import org.http4s.Uri

final case class SolrConfig(
    baseUrl: Uri,
    core: String,
    maybeUser: Option[SolrConfig.SolrUser],
    logMessageBodies: Boolean
)

object SolrConfig:
  final case class SolrPassword(value: String) {
    override def toString(): String = "***"
  }
  final case class SolrUser(username: String, password: SolrPassword)
