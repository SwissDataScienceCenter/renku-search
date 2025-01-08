package io.renku.json

import scala.quoted.*

import io.bullet.borer.derivation.key

private object LabelsMacro:
  inline def findLabels[T]: Seq[String] = ${ findLabelsImpl[T] }

  private def findLabelsImpl[T: Type](using q: Quotes): Expr[Seq[String]] = {
    import q.reflect.*

    val keyAnno = TypeRepr.of[key]

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
