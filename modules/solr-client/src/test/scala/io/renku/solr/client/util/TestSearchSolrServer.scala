package io.renku.solr.client.util

import cats.effect.{ExitCode, IO, IOApp}

import io.renku.servers.SolrServer

/** This is a utility to start a Solr server for manual testing */
object TestSearchSolrServer extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    (IO(SolrServer.start()) >> IO.never[ExitCode]).as(ExitCode.Success)
