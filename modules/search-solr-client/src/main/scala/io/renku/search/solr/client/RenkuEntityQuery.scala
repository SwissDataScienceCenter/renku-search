package io.renku.search.solr.client

import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.query.SolrQuery
import io.renku.search.solr.query.SolrToken
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.*
import io.renku.solr.client.facet.Facet
import io.renku.solr.client.facet.Facets
import io.renku.solr.client.schema.FieldName

/** Convert the user query into a final query that is send to SOLR. */
object RenkuEntityQuery:
  private val typeTerms = Facet.Terms(
    EntityDocumentSchema.Fields.entityType,
    EntityDocumentSchema.Fields.entityType
  )

  private val creatorDetails: FieldName = FieldName("creatorDetails")
  private val namespaceDetails: FieldName = FieldName("namespaceDetails")

  def apply(role: SearchRole, sq: SolrQuery, limit: Int, offset: Int): QueryData =
    QueryData(QueryString(sq.query.value, limit, offset))
      .addFilter(
        SolrToken.kindIs(DocumentKind.FullEntity).value,
        SolrToken.namespaceExists.value,
        SolrToken.createdByExists.value,
        "{!join from=namespace to=namespace}(_type:User OR _type:Group)"
      )
      .addFilter(constrainRole(role).map(_.value)*)
      .withSort(sq.sort)
      .withFacet(Facets(typeTerms))
      .withFields(FieldName.all, FieldName.score)
      .addSubQuery(
        creatorDetails,
        SubQuery(
          "{!terms f=id v=$row.createdBy}",
          "{!terms f=_kind v=fullentity}",
          1
        ).withFields(FieldName.all)
      )
      .addSubQuery(
        namespaceDetails,
        SubQuery(
          "{!terms f=namespace v=$row.namespace}",
          "(_type:User OR _type:Group) AND _kind:fullentity",
          1
        ).withFields(FieldName.all)
      )

  private def constrainRole(role: SearchRole) = role match
    case SearchRole.Anonymous =>
      Seq(SolrToken.publicOnly)

    case SearchRole.User(id) =>
      Seq(SolrToken.forUser(id))

    case SearchRole.Admin(_) =>
      Seq.empty
