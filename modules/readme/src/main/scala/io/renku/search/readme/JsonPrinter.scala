package io.renku.search.readme

import io.bullet.borer.{Encoder, Json}

object JsonPrinter:

  def block[A: Encoder](value: A): Unit =
    CodeBlock.lang("json")(
      pretty(Json.encode(value).toUtf8String)
    )

  def str[A: Encoder](value: A) = pretty(Json.encode(value).toUtf8String)

  def pretty(json: String): String = pp(json)

  // yes, it is ugly, but only for the readme and avoids yet another library
  @annotation.tailrec
  private def pp(
      in: String,
      depth: Int = 0,
      inQuote: Boolean = false,
      res: String = ""
  ): String =
    def spnl(n: Int) = "\n" + List.fill(n)(" ").mkString
    in.headOption match {
      case None => res
      case Some('"') =>
        pp(in.drop(1), depth, !inQuote, res + '"')
      case Some(' ') =>
        val next = if (inQuote) res + ' ' else res
        pp(in.drop(1), depth, inQuote, next)
      case Some(c) if c == '{' || c == '[' =>
        val next = res + c + spnl((depth + 1) * 2)
        pp(in.drop(1), depth + 1, inQuote, next)
      case Some(c) if c == '}' || c == ']' =>
        val next = res + spnl((depth - 1) * 2) + c
        pp(in.drop(1), depth - 1, inQuote, next)
      case Some(':') =>
        pp(in.drop(1), depth, inQuote, res + ": ")
      case Some(',') =>
        val next = res + ',' + Option.when(!inQuote)(spnl(depth * 2)).getOrElse("")
        pp(in.drop(1), depth, inQuote, next)
      case Some(c) =>
        pp(in.drop(1), depth, inQuote, res + c)
    }
