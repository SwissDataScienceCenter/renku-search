package io.renku.search.cli.perftests

import cats.MonadThrow
import fs2.Stream

private trait EventsGenerator[F[_]]:
  def generate(count: Int): Stream[F, NewProjectEvents]

private object EventsGenerator:
  def apply[F[_]: MonadThrow: ModelTypesGenerators](
      docsCreator: DocumentsCreator[F]
  ): EventsGenerator[F] =
    new EventsGeneratorImpl[F](ProjectEventsGenerator[F](docsCreator))

private class EventsGeneratorImpl[F[_]](
    projectCreatedGenerator: ProjectEventsGenerator[F]
) extends EventsGenerator[F]:

  override def generate(count: Int): Stream[F, NewProjectEvents] =
    projectCreatedGenerator.newProjectEvents
      .take(count)
