package io.renku.search.solr.documents

import io.bullet.borer.Json
import io.renku.search.GeneratorSyntax.*
import io.renku.search.solr.client.SolrDocumentGenerators
import munit.FunSuite

class EntityEncodingSpec extends FunSuite:

  test("full project with kind and type"):
    val project = SolrDocumentGenerators.projectDocumentGen.generateOne
    val pJson = Json.encode(project).toUtf8String
    val p = Json.decode(pJson.getBytes).to[EntityDocument].value
    assert(pJson.contains(""""_kind":"fullentity""""))
    assert(pJson.contains(""""_type":"Project""""))
    assertEquals(p, project)
    val projectADT: EntityDocument = project
    assertEquals(Json.encode(projectADT).toUtf8String, pJson)

  test("encode partial project with kind and type"):
    val partial = SolrDocumentGenerators.partialProjectGen.generateOne
    val pJson = Json.encode(partial).toUtf8String
    val p = Json.decode(pJson.getBytes).to[PartialEntityDocument].value
    assert(pJson.contains(""""_kind":"partialentity""""))
    assert(pJson.contains(""""_type":"Project""""))
    assertEquals(p, partial)
    val projectADT: PartialEntityDocument = partial
    assertEquals(Json.encode(projectADT).toUtf8String, pJson)

  test("encode user with kind, type and visibility"):
    val user = SolrDocumentGenerators.userDocumentGen.generateOne
    val uJson = Json.encode(user).toUtf8String
    val u = Json.decode(uJson.getBytes).to[EntityDocument].value
    assert(uJson.contains(""""_kind":"fullentity""""))
    assert(uJson.contains(""""_type":"User""""))
    assert(uJson.contains(""""visibility":"public""""))
    assertEquals(u, user)
    val userADT: EntityDocument = user
    assertEquals(Json.encode(userADT).toUtf8String, uJson)
