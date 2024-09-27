package io.renku.search.readme

object CodeBlock:
  def plainLines(lines: String*): Unit =
    plain(lines.mkString("\n"))

  def plain(content: String): Unit = apply(content, "")
  def lang(lang: String)(content: String): Unit = apply(content, lang)
  def apply(content: String, lang: String = ""): Unit =
    println(s"``` $lang")
    println(content)
    println("```")
