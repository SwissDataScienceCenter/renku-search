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

package io.renku.json

import scala.quoted.*

import io.bullet.borer.derivation.key

private object LabelsMacro:
  inline def findLabels[T]: Seq[String] = ${ findLabelsImpl[T] }

  private def findLabelsImpl[T: Type](using q: Quotes): Expr[Seq[String]] = {
    import q.reflect.*

    val keyAnno = TypeRepr.of[io.bullet.borer.derivation.key]

    def keyAnnotation(s: Symbol): Expr[Option[String]] = {
      val key = s.annotations.find(_.tpe <:< keyAnno).map {
        case Apply(_, List(Literal(StringConstant(x)))) => x
        case e =>
          report.errorAndAbort("Only strings supported for 'key' annotation", e.pos)
      }
      Expr(key)
    }

    val sym = TypeRepr.of[T].typeSymbol
    val caseParams = sym.caseFields
    val fields = Varargs(caseParams.map(sym => Expr(sym.name)))
    val fieldAnns = Varargs(caseParams.map(keyAnnotation))
    '{
      $fields.zip($fieldAnns).map(t => t._2.getOrElse(t._1))
    }
  }
