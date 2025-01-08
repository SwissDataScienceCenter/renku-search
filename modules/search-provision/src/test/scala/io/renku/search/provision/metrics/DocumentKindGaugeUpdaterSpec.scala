package io.renku.search.provision.metrics

import cats.effect.IO

import io.prometheus.client.Collector
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.EntityType
import io.renku.search.model.EntityType.User
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.client.SolrDocumentGenerators.userDocumentGen
import io.renku.search.solr.documents.DocumentKind
import munit.CatsEffectSuite

class DocumentKindGaugeUpdaterSpec extends CatsEffectSuite with SearchSolrSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, searchSolrClient)

  test("update should fetch the data and insert 0 for missing kinds"):
    val user = userDocumentGen.generateOne
    val gauge = TestGauge(User)
    for {
      client <- IO(searchSolrClient())
      gaugeUpdater = new DocumentKindGaugeUpdater[IO](client, gauge)
      _ <- client.upsert(Seq(user.widen))
      _ <- gaugeUpdater.update()
    } yield assert {
      gauge.acc(DocumentKind.FullEntity) >= 1d &&
      gauge.acc(DocumentKind.PartialEntity) == 0d
    }

  private class TestGauge(override val entityType: EntityType) extends DocumentKindGauge:
    var acc: Map[DocumentKind, Double] = Map.empty

    override def set(l: DocumentKind, v: Double): Unit =
      acc = acc + (l -> v)

    override def asJCollector: Collector = ???
