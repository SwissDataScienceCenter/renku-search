package io.renku.solr.client.schema

import scala.io.Source

import io.bullet.borer.Json
import io.renku.solr.client.SchemaResponse
import io.renku.solr.client.schema.SchemaCommand.DeleteType
import munit.FunSuite

class BorerJsonCodecTest extends FunSuite with SchemaJsonCodec {

  test("encode schema command: delete type"):
    val v = DeleteType(TypeName("integer"))
    assertEquals(
      Json.encode(v).toUtf8String,
      """{"delete-field-type":{"name":"integer"}}"""
    )

  test("encode schema command: add"):
    val v = SchemaCommand.Add(Field(FieldName("description"), TypeName("integer")))
    assertEquals(
      Json.encode(v).toUtf8String,
      """{"add-field":{"name":"description","type":"integer"}}"""
    )

  test("encode multiple schema commands into a single object"):
    val vs = Seq(
      DeleteType(TypeName("integer")),
      DeleteType(TypeName("float")),
      SchemaCommand.Add(
        Field(FieldName("description"), TypeName("text"), required = true)
      )
    )
    assertEquals(
      Json.encode(vs).toUtf8String,
      """{"delete-field-type":{"name":"integer"},"delete-field-type":{"name":"float"},"add-field":{"name":"description","type":"text","required":true}}""".stripMargin
    )

  test("decode schema response"):
    val schemaResponseText = Source.fromResource("schema-response.json").mkString
    val result = Json.decode(schemaResponseText.getBytes()).to[SchemaResponse].value
    assertEquals(result.schema.copyFields.size, 16)
    assertEquals(result.schema.dynamicFields.size, 69)
    assertEquals(result.schema.fields.size, 30)
    assertEquals(result.schema.fieldTypes.size, 73)
    assert(result.schema.fields.exists(_.name == FieldName("_kind")))
    assert(result.schema.copyFields.exists(_.source == FieldName("description")))

  test("encode filter with settings"):
    val cfg = Filter.EdgeNGramSettings()
    val ft = Filter.edgeNGram(cfg)
    val json = Json.encode(ft).toUtf8String
    assertEquals(
      json,
      s"""{"name":"edgeNGram","minGramSize":"${cfg.minGramSize}","maxGramSize":"${cfg.maxGramSize}","preserveOriginal":"${cfg.preserveOriginal}"}"""
    )

  test("decode filter with settings"):
    val jsonStr =
      """{"name":"edgeNGram","minGramSize":"3","maxGramSize":"6","preserveOriginal":"true"}"""
    val result = Json.decode(jsonStr.getBytes()).to[Filter].value
    val expect = Filter.edgeNGram(Filter.EdgeNGramSettings(3, 6, true))
    assertEquals(result, expect)
}
