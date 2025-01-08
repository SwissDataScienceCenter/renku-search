package io.renku.solr.client.util

import cats.effect.*

import io.renku.search.GeneratorSyntax.*
import io.renku.search.LoggingConfigure
import io.renku.solr.client.*
import munit.CatsEffectFixtures
import org.scalacheck.Gen

trait SolrClientBaseSuite
    extends SolrServerSuite
    with LoggingConfigure
    with SolrTruncate
    with CatsEffectFixtures:

  private val coreNameGen: Gen[String] =
    Gen
      .choose(5, 12)
      .flatMap(n => Gen.listOfN(n, Gen.alphaChar))
      .map(_.mkString)
      .map(name => s"test-core-$name")

  val solrClientR: Resource[IO, SolrClient[IO]] =
    for
      serverUri <- Resource.eval(IO(solrServer()))
      coreName <- Resource.eval(IO(coreNameGen.generateOne))
      cfg = SolrConfig(serverUri, coreName, None, false)
      client <- SolrClient[IO](cfg)
      _ <- Resource.make(createSolrCore(client, coreName))(_ =>
        deleteSolrCore(client, coreName).start.void
      )
    yield client

  val solrClient = ResourceSuiteLocalFixture("solr-client", solrClientR)

  def createSolrCore(client: SolrClient[IO], name: String): IO[Unit] =
    IO.blocking(solrServerValue.createCore(name).get)

  def deleteSolrCore(client: SolrClient[IO], name: String): IO[Unit] =
    IO.blocking(solrServerValue.deleteCore(name).get)
