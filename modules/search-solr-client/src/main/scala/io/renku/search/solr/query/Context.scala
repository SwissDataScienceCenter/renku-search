package io.renku.search.solr.query

import java.time.Instant
import java.time.ZoneId

import cats.Applicative
import cats.effect.{Clock, Sync}
import cats.syntax.all.*

import io.renku.search.solr.SearchRole

trait Context[F[_]]:
  def currentTime: F[Instant]
  def zoneId: F[ZoneId]
  def role: SearchRole

object Context:
  def forSync[F[_]: Sync](searchRole: SearchRole): Context[F] =
    new Context[F]:
      val currentTime: F[Instant] = Clock[F].realTimeInstant
      val zoneId: F[ZoneId] = Sync[F].delay(ZoneId.systemDefault())
      val role = searchRole

  def fixed[F[_]: Applicative](
      time: Instant,
      zone: ZoneId,
      searchRole: SearchRole
  ): Context[F] =
    new Context[F]:
      val currentTime = time.pure[F]
      val zoneId = zone.pure[F]
      val role = searchRole

  def fixedZone[F[_]: Applicative: Clock](
      zone: ZoneId,
      searchRole: SearchRole
  ): Context[F] =
    new Context[F]:
      val currentTime = Clock[F].realTimeInstant
      val zoneId = zone.pure[F]
      val role = searchRole
