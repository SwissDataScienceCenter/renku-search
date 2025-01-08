package io.renku.search.common

import cats.Monoid

import scodec.bits.BitVector
import scodec.bits.ByteVector

trait ScodecInstances:

  given Monoid[ByteVector] = new Monoid[ByteVector] {
    def empty = ByteVector.empty
    def combine(x: ByteVector, y: ByteVector) = x ++ y
  }

  given Monoid[BitVector] = new Monoid[BitVector] {
    def empty = BitVector.empty
    def combine(x: BitVector, y: BitVector) = x ++ y
  }

object ScodecInstances extends ScodecInstances
