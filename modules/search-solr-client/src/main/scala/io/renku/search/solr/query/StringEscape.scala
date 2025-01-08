package io.renku.search.solr.query

private[query] object StringEscape:

  // Escapes query characters for solr. This is taken from here:
  // https://github.com/apache/solr/blob/bcb9f144974ed07aa3b66766302474542067b522/solr/solrj/src/java/org/apache/solr/client/solrj/util/ClientUtils.java#L163
  // to not introduce too many dependencies only for this little function
  private val defaultSpecialChars = "\\+-!():^[]\"{}~*?|&;/"

  def escape(s: String, specialChars: String): String =
    inline def isSpecial(c: Char) = c.isWhitespace || specialChars.contains(c)
    val sb = new StringBuilder();
    s.foreach { c =>
      if (isSpecial(c)) sb.append('\\')
      sb.append(c)
    }
    sb.toString

  def queryChars(s: String): String = escape(s, defaultSpecialChars)
