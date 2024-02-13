package io.renku.search.query

import cats.syntax.all.*
import cats.parse.Parser
import munit.FunSuite

import java.time.Instant
import java.time.temporal.ChronoUnit

class DateTimeParserSpec extends FunSuite {

  extension [A](self: Parser[A])
    def run(str: String): A =
      self.parseAll(str) match
        case Left(err) =>
          Console.err.println(str)
          Console.err.println(err.show)
          sys.error("parsing failed")

        case Right(v) => v

  test("playing") {
    println(
      DateTimeParser.utc.instantMin.run("2023-05-10T11")
    )
    println(
      DateTimeParser.utc.dateMax.run("2024-02")
    )
    println(
      DateTimeParser.utc.instantMax.run("2023-02")
    )
    println(
      DateTimeParser.utc.instantMin.run(
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString
      )
    )
  }
}
