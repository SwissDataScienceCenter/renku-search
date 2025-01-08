package io.renku.search.query.parse

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.syntax.all.*

trait ParserSuite {

  extension [A](self: Parser[A])
    def run(str: String): A =
      self.parseAll(str) match
        case Left(err) =>
          Console.err.println(str)
          Console.err.println(err.show)
          sys.error("parsing failed")

        case Right(v) => v

  def nel[A](a: A, more: A*): NonEmptyList[A] =
    NonEmptyList(a, more.toList)
}
