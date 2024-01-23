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

import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try

object SolrServer {

  private val startRequests = new AtomicInteger(0)

  def start: ClassLoader => Unit = { cl =>
    if (startRequests.getAndIncrement() == 0) call("start")(cl)
  }

  def stop: ClassLoader => Unit = { cl =>
    if (startRequests.decrementAndGet() == 0)
      Try(call("forceStop")(cl))
        .recover { case err => err.printStackTrace() }
  }

  private def call(methodName: String): ClassLoader => Unit = classLoader => {
    val clazz = classLoader.loadClass("io.renku.solr.client.util.SolrServer$")
    val method = clazz.getMethod(methodName)
    val instance = clazz.getField("MODULE$").get(null)
    method.invoke(instance)
  }
}