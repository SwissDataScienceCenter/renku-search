package io.renku.solr.client.util

import cats.effect.IO
import cats.syntax.all.*

import io.renku.search.http.ResponseLogging
import io.renku.solr.client.schema.{FieldName, SchemaCommand, TypeName}
import io.renku.solr.client.{QueryString, SolrClient}

trait SolrTruncate {

  def truncateAll(
      client: SolrClient[IO]
  )(fields: Seq[FieldName], types: Seq[TypeName]): IO[Unit] =
    truncateQuery(client)("*:*", fields, types)

  def truncateQuery(
      client: SolrClient[IO]
  )(query: String, fields: Seq[FieldName], types: Seq[TypeName]): IO[Unit] =
    for {
      _ <- client.delete(QueryString(query))
      _ <- fields
        .map(SchemaCommand.DeleteField.apply)
        .traverse_(modifyIgnoreError(client))
      _ <- types
        .map(SchemaCommand.DeleteType.apply)
        .traverse_(modifyIgnoreError(client))
    } yield ()

  private def modifyIgnoreError(client: SolrClient[IO])(c: SchemaCommand) =
    client
      .modifySchema(Seq(c), ResponseLogging.Ignore)
      .attempt
      .void
}
