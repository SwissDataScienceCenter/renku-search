package io.renku.search.readme

object BulletPoints:

  def apply(lines: Seq[String]): Unit =
    println(lines.mkString("- ", "\n- ", ""))
