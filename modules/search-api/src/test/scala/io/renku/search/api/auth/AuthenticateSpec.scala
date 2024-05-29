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

import cats.Id
import cats.syntax.all.*

import io.renku.openid.keycloak.JwtError
import io.renku.openid.keycloak.JwtVerify
import io.renku.search.api.data.{AuthContext, AuthToken}
import io.renku.search.jwt.RenkuToken
import io.renku.search.model
import munit.FunSuite
import scribe.{Level, LogRecord, Scribe}

class AuthenticateSpec extends FunSuite:

  val adminToken: RenkuToken = RenkuToken(
    subject = Some("admin"),
    realmAccess = Some(RenkuToken.Access(Set("renku-admin")))
  )
  val userToken: RenkuToken = RenkuToken(subject = Some("user"))
  val noSubjectToken: RenkuToken = RenkuToken()
  val verifyError: JwtError =
    JwtError.JwtValidationError("", None, None, new RuntimeException)

  def makeAuthenticate(result: RenkuToken | JwtError): AuthenticateSpec.TestAuthenticate =
    val verify = JwtVerify.fixed[Id](result)
    AuthenticateSpec.testAuthenticate(verify)

  test("anonymous when no token, not calling verify"):
    assertEquals(
      makeAuthenticate(verifyError).apply(AuthToken.None),
      Right(AuthContext.anonymous)
    )

  test("anonymous when anon-id, not calling verify"):
    assertEquals(
      makeAuthenticate(verifyError).apply(AuthToken.AnonymousId(model.Id("123"))),
      Right(AuthContext.anonymousId("123"))
    )

  test("failure when verify fails, log warning"):
    val auth = makeAuthenticate(verifyError)
    val result = auth(AuthToken.JwtToken("dummy"))
    assert(result.isLeft)
    assert(auth.logged.nonEmpty)
    assertEquals(auth.logged.head.level, Level.Warn)

  test("fail when claim has no subject, log warning"):
    val auth = makeAuthenticate(noSubjectToken)
    val result = auth(AuthToken.JwtToken("dummy"))
    assert(result.isLeft)
    assert(auth.logged.nonEmpty)
    assertEquals(auth.logged.head.level, Level.Warn)

  test("success with admin token"):
    val ctx = makeAuthenticate(adminToken).apply(AuthToken.JwtToken("dummy"))
    assertEquals(
      ctx.map(_.fold(_.userId.value.some, _ => None, _ => None, None)),
      adminToken.subject.asRight[String]
    )

  test("success with user token"):
    val ctx = makeAuthenticate(userToken).apply(AuthToken.JwtToken("dummy"))
    assertEquals(
      ctx.map(_.fold(_ => None, _.userId.value.some, _ => None, None)),
      userToken.subject.asRight[String]
    )

object AuthenticateSpec:
  trait TestAuthenticate extends Authenticate[Id]:
    def logged: List[LogRecord]

  def testAuthenticate(verify: JwtVerify[Id]): TestAuthenticate =
    new TestAuthenticate {
      var records: List[LogRecord] = Nil
      def logger: Scribe[Id] = new Scribe[Id] {
        def log(record: scribe.LogRecord): Id[Unit] =
          records = record :: records
      }
      val inner = Authenticate[Id](verify, logger)

      def logged: List[LogRecord] = records.reverse
      def apply(token: AuthToken) = inner(token)
    }
