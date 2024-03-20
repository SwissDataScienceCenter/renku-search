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

package io.renku.search.perftests

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all.*
import cats.{MonadThrow, Show}

import scala.util.CommandLineParser as CLP
import scala.util.CommandLineParser.{FromString, ParseError}

final private case class Config(itemsToGenerate: Int, randommerIoApiKey: String)

private object Config:

  def parse[F[_]: MonadThrow](args: List[String]): F[Config] =
    parse(args.toArray)

  def parse[F[_]: MonadThrow](args: Array[String]): F[Config] =
    MonadThrow[F].fromEither {
      (args.parse[Int](idx = 0), args.parse[String](idx = 1))
        .mapN(Config.apply)
        .toEither
        .leftMap(_.mkString_("; "))
        .leftMap(new Exception(_))
    }

  extension (args: Array[String])
    def parse[T](idx: Int)(using FromString[T]): ValidatedNel[ParseError, T] =
      Validated
        .catchOnly[ParseError](CLP.parseArgument[T](args, idx))
        .toValidatedNel

  private given Show[ParseError] = Show.show(_.getMessage)
