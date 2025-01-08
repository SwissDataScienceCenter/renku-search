package io.renku.search.provision.handler

import io.renku.search.events.*
import io.renku.search.model.Id

trait IdExtractor[A]:
  def getId(a: A): Id

object IdExtractor:
  def apply[A](using e: IdExtractor[A]): IdExtractor[A] = e

  def create[A](f: A => Id): IdExtractor[A] =
    (a: A) => f(a)

  def createStr[A](f: A => String): IdExtractor[A] =
    (a: A) => Id(f(a))

  given [A <: RenkuEventPayload]: IdExtractor[A] =
    create(_.id)

  given IdExtractor[EntityOrPartial] =
    create(_.id)

  given IdExtractor[Id] =
    create(identity)
