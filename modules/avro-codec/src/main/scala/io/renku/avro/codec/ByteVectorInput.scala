/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.avro.codec

import org.apache.avro.file.SeekableInput
import scodec.bits.ByteVector

import java.io.InputStream

final class ByteVectorInput(val bytes: ByteVector)
    extends InputStream
    with SeekableInput {

  private[this] var position: Long = 0

  override def seek(p: Long): Unit =
    position = p

  override def tell(): Long =
    getPosition

  def getPosition: Long = position

  override val length: Long = bytes.length

  override def read(): Int =
    if (position >= length) -1
    else {
      val b = bytes(position).toInt
      position = position + 1
      b
    }

  override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (len <= 0) 0
    else if (position >= length) -1
    else {
      val until = math.min(position + len, length)
      bytes.slice(position, until).copyToArray(b, off)
      val bytesRead = (until - position).toInt
      position = until
      bytesRead
    }

  override def skip(n: Long): Long = {
    val until = math.min(position + n, length)
    val skipped = until - position
    position = until
    skipped
  }

  override def available(): Int = (length - position).toInt

  override def close(): Unit = ()
}
