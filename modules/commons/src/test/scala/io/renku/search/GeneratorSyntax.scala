package io.renku.search

import cats.arrow.FunctionK
import cats.effect.IO
import fs2.Stream

import org.scalacheck.Gen

trait GeneratorSyntax:

  extension [A](self: Gen[A])
    @annotation.tailrec
    final def generateOne: A =
      self.sample match
        case Some(a) => a
        case None    => generateOne

    def generateSome: Option[A] = Some(generateOne)

    def generateList: List[A] = Gen.listOf(self).generateOne

    def generateList(min: Int, max: Int): List[A] =
      asListOfN(min, max).generateOne

    def stream: Stream[Gen, A] =
      Stream.repeatEval(self)

    def asListOfN(min: Int = 1, max: Int = 8): Gen[List[A]] =
      Gen.choose(min, max).flatMap(Gen.listOfN(_, self))

    def asOption: Gen[Option[A]] =
      Gen.option(self)

    def asSome: Gen[Option[A]] =
      self.map(Some(_))

  extension [A](self: Stream[Gen, A])
    def toIO: Stream[IO, A] =
      self.translate(FunctionK.lift[Gen, IO]([X] => (gx: Gen[X]) => IO(gx.generateOne)))

object GeneratorSyntax extends GeneratorSyntax
