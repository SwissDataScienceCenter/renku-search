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

package io.renku.search.api.auth

import cats.syntax.all.*

import io.renku.search.api.data.AuthContext
import io.renku.search.jwt.RenkuToken
import munit.FunSuite

class ClaimToContextSpec extends FunSuite:

  val adminToken: RenkuToken = RenkuToken(
    subject = Some("admin"),
    realmAccess = Some(RenkuToken.Access(Set("renku-admin")))
  )
  val userToken: RenkuToken = RenkuToken(subject = Some("user"))
  val noSubjectToken: RenkuToken = RenkuToken()

  def assertFailure(actual: ClaimToContext.Failure, expect: Throwable) =
    assertEquals(actual.cause, expect)

  test("renku token without subject"):
    ClaimToContext(noSubjectToken) match
      case Left(err) =>
        assertFailure(err, ClaimToContext.ClaimHasNoSubject(noSubjectToken))
      case Right(_) => fail("expected error")

  test("renku token is admin"):
    assert(adminToken.isAdmin)
    ClaimToContext(adminToken) match
      case Left(err) => throw err.cause
      case Right(AuthContext.Admin(id)) =>
        assertEquals(id.value.some, adminToken.subject)
      case Right(ctx) => fail(s"Invalid auth context: $ctx")

  test("renku token is user"):
    assert(!userToken.isAdmin)
    ClaimToContext(userToken) match
      case Left(err) => throw err.cause
      case Right(AuthContext.Authenticated(id)) =>
        assertEquals(id.value.some, userToken.subject)
      case Right(ctx) => fail(s"Invalid auth context: $ctx")
