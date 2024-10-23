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

package io.renku.search.logging

import scribe.Level
import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.writer.SystemOutWriter

object LoggingSetup:

  def doConfigure(verbosity: Int, additionalHandler: Option[LogHandler] = None): Unit =
    println(s">> Setting up logging with verbosity=$verbosity")
    val root = scribe.Logger.root.clearHandlers().clearModifiers()
    verbosity match
      case n if n < 0 =>
        ()

      case 0 =>
        root.withMinimumLevel(Level.Error).replace()
        configureRenkuSearch(Level.Error, additionalHandler)
        ()

      case 1 =>
        root.withMinimumLevel(Level.Warn).replace()
        configureRenkuSearch(Level.Warn, additionalHandler)
        ()

      case 2 =>
        root.withMinimumLevel(Level.Warn).replace()
        configureRenkuSearch(Level.Info, additionalHandler)

      case 3 =>
        root.withMinimumLevel(Level.Info).replace()
        configureRenkuSearch(Level.Debug, additionalHandler)

      case 4 =>
        root.withMinimumLevel(Level.Info).replace()
        configureRenkuSearch(Level.Trace, additionalHandler)

      case _ =>
        root.withMinimumLevel(Level.Debug).replace()
        configureRenkuSearch(Level.Trace, additionalHandler)

  private def configureRenkuSearch(
      level: Level,
      additionalHandler: Option[LogHandler]
  ): Unit = {
    val renkuLogger = scribe
      .Logger("io.renku")
      .clearHandlers()
      .withMinimumLevel(level)
      .withHandler(
        formatter = Formatter.classic,
        writer = SystemOutWriter,
        minimumLevel = Some(level)
      )

    additionalHandler
      .map(h => renkuLogger.withHandler(h).replace())
      .getOrElse(renkuLogger.replace())
    ()
  }
