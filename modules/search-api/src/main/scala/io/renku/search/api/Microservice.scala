package io.renku.search.api

import cats.effect.*

import io.renku.search.logging.LoggingSetup
import io.renku.search.sentry.*
import io.renku.search.sentry.scribe.SentryHandler

object Microservice extends IOApp:
  private val logger = _root_.scribe.cats.io
  private val loadConfig = SearchApiConfig.config.load[IO]
  val pathPrefix = List("api", "search")

  override def run(args: List[String]): IO[ExitCode] =
    createServer.useForever

  def createServer =
    for {
      config <- Resource.eval(loadConfig)
      sentry <- Sentry[IO](
        config.sentryConfig.withTag(TagName.service, TagValue.searchApi)
      )
      _ <- Resource.eval(
        IO(
          LoggingSetup.doConfigure(
            config.verbosity,
            Some(SentryHandler(sentry)(using runtime))
          )
        )
      )
      app <- ServiceRoutes[IO](config, pathPrefix)
      server <- SearchServer.create[IO](config, app, sentry)
      _ <- Resource.eval(
        logger.info(
          s"Search microservice running: ${config.httpServerConfig}/${config.sentryConfig}"
        )
      )
      startupEvent <- Resource.eval(makeStartupEvent(config))
      _ <- Resource.eval(sentry.capture(startupEvent))
    } yield server

  private def makeStartupEvent(config: SearchApiConfig): IO[SentryEvent] =
    SentryEvent.create[IO](Level.Info, s"Search API starting up with $config")
