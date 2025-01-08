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
