package io.renku.search.cli.perftests

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS

import cats.Monad
import cats.effect.IO
import cats.effect.std.{Random, UUIDGen}
import cats.syntax.all.*

import io.renku.search.events.*
import io.renku.search.model.*

private object ModelTypesGenerators:

  def forIO: IO[ModelTypesGenerators[IO]] =
    given UUIDGen[IO] = UUIDGen.fromSync[IO]
    Random.scalaUtilRandom[IO].map { u =>
      given Random[IO] = u
      new ModelTypesGenerators[IO] {}
    }

  def apply[F[_]](using ev: ModelTypesGenerators[F]): ModelTypesGenerators[F] = ev

private trait ModelTypesGenerators[F[_]: Monad: Random: UUIDGen]:

  def generateRequestId: F[RequestId] =
    UUIDGen.randomString[F].map(RequestId(_))

  def generateId: F[Id] =
    UUIDGen.randomString[F].map(_.replace("-", "").toUpperCase).map(Id(_))
  def generateCreationDate: F[CreationDate] =
    Random[F]
      .betweenLong(
        Instant.now().minus(5 * 365, DAYS).toEpochMilli,
        Instant.now().toEpochMilli
      )
      .map(Instant.ofEpochMilli)
      .map(CreationDate.apply)
  def generateVisibility: F[Visibility] =
    Random[F].shuffleList(Visibility.values.toList).map(_.head)

  def generateRole: F[MemberRole] =
    Random[F].shuffleList(MemberRole.values.toList).map(_.head)
