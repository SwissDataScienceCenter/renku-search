package io.renku.search.query

private object Strings {
  def lowerFirst(s: String) =
    if (s == null || s.isEmpty || s.charAt(0).isLower) s
    else s.updated(0, s.charAt(0).toLower)
}
