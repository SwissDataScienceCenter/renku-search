package io.renku.solr.client.util

import io.renku.servers.SolrServer
import munit.Fixture
import org.http4s.Uri

/** Starts the solr server if not already running.
  *
  * This is here for running single tests from outside sbt. Within sbt, the solr server is
  * started before any test is run and therefore will live for the entire test run.
  */
trait SolrServerSuite:

  lazy val solrServerValue = SolrServer

  val solrServer: Fixture[Uri] =
    new Fixture[Uri]("solr-server"):
      private var serverUri: Option[Uri] = None
      def apply(): Uri = serverUri match
        case Some(u) => u
        case None    => sys.error(s"Fixture $fixtureName not initialized")

      override def beforeAll(): Unit =
        solrServerValue.start()
        serverUri = Some(Uri.unsafeFromString(solrServerValue.url))

      override def afterAll(): Unit =
        solrServerValue.stop()
