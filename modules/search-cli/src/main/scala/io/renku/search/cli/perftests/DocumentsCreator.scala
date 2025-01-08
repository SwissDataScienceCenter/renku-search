package io.renku.search.cli.perftests

import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network

import io.renku.search.solr.documents.{Project, User}

private trait DocumentsCreator[F[_]]:
  def findUser: Stream[F, User]
  def findProject: Stream[F, (Project, List[User])]

private object DocumentsCreator:
  def make[F[_]: Async: Network: ModelTypesGenerators](
      apiKey: String
  ): Resource[F, DocumentsCreator[F]] =
    RandommerIoDocsCreator.make[F](apiKey)
