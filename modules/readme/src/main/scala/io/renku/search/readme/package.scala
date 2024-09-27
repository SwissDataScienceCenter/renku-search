package io.renku.search

package object readme {

  extension (self: String)
    def backticks: String = s"`${self}`"
    def quoted: String = s"\"${self}\""
}
