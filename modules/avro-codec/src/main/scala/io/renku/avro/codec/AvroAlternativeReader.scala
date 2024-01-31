package io.renku.avro.codec

import org.apache.avro.AvroRuntimeException
import scodec.bits.ByteVector

import scala.util.{Failure, Success, Try}

/** Utility that first tries binary decoding an input and if that fails or yields an empty
  * result, it falls back using json decoding.
  *
  * Note that this is not safe to use for all schemas, it can fail for primitive types
  * like INT or LONG, while it is working for RECORDs.
  */
private object AvroAlternativeReader:

  extension (self: AvroReader)
    def readBinaryOrJson[T: AvroDecoder](in: ByteVector): Seq[T] =
      AvroAlternativeReader.readBinaryOrJson[T](in, self)

  def readBinaryOrJson[T: AvroDecoder](in: ByteVector, reader: AvroReader): Seq[T] =
    Try(reader.read[T](in)) match
      case Success(v) if v.nonEmpty => v
      case Success(_) =>
        Try(reader.readJson[T](in)) match
          case Success(v) => v
          case Failure(ex) =>
            throw AlternativeReadError("Read json after empty binary failed", ex, None)

      case Failure(ex1: AvroRuntimeException) =>
        Try(reader.readJson[T](in)) match
          case Success(v) => v
          case Failure(ex2) =>
            throw AlternativeReadError("Reading binary or json failed!", ex1, Some(ex2))
      case Failure(ex) =>
        throw ex

  private class AlternativeReadError(
      msg: String,
      cause: Throwable,
      fallbackCause: Option[Throwable]
  ) extends AvroRuntimeException(msg, cause):
    fallbackCause.foreach(addSuppressed)
    override def fillInStackTrace(): Throwable = this
