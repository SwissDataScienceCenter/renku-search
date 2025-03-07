package io.renku.search.solr.query

import java.time.{Instant, ZoneId}

import cats.Id
import cats.data.NonEmptyList as Nel

import io.renku.search.model
import io.renku.search.model.MemberRole
import io.renku.search.query.*
import io.renku.search.query.{Comparison, FieldTerm}
import io.renku.search.solr.SearchRole
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import munit.FunSuite

class LuceneQueryEncoderSpec extends FunSuite with LuceneQueryEncoders:

  val refDate: Instant = Instant.parse("2024-02-27T15:34:55Z")
  val utc: ZoneId = ZoneId.of("UTC")

  val ctx: Context[Id] = Context.fixed(refDate, utc, SearchRole.admin(model.Id("admin")))
  val createdEncoder = SolrTokenEncoder[Id, FieldTerm.Created]

  test("use date-max for greater-than"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.GreaterThan, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(SolrToken.createdDateGt(pd.instantMax(utc)))
    )

  test("use date-min for lower-than"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.LowerThan, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(SolrToken.createdDateLt(pd.instantMin(utc)))
    )

  test("created comparison is"):
    val cToday: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(RelativeDate.Today)))
    assertEquals(
      createdEncoder.encode(ctx, cToday),
      SolrQuery(SolrToken.createdDateIs(refDate))
    )

  test("single range"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(
        SolrToken.fieldIs(
          SolrField.creationDate,
          SolrToken.fromDateRange(pd.instantMin(utc), pd.instantMax(utc))
        )
      )
    )

  test("multiple range"):
    val pd1 = PartialDateTime.unsafeFromString("2023-05")
    val pd2 = PartialDateTime.unsafeFromString("2023-08")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(pd1), DateTimeRef(pd2)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(
        List(
          SolrToken.fieldIs(
            SolrField.creationDate,
            SolrToken.fromDateRange(pd1.instantMin(utc), pd1.instantMax(utc))
          ),
          SolrToken.fieldIs(
            SolrField.creationDate,
            SolrToken.fromDateRange(pd2.instantMin(utc), pd2.instantMax(utc))
          )
        ).foldOr
      )
    )

  List(
    SearchRole.Admin(model.Id("admin")),
    SearchRole.Anonymous,
    SearchRole.User(model.Id("5"))
  ).foreach { role =>
    val encoder = SolrTokenEncoder[Id, FieldTerm.RoleIs]
    val ctx = Context.fixed[Id](refDate, utc, role)
    val encode = encoder.encode(ctx, _)

    test(s"role filter: $role"):
      val memberQuery: FieldTerm.RoleIs = FieldTerm.RoleIs(Nel.of(MemberRole.Member))
      val ownerQuery: FieldTerm.RoleIs = FieldTerm.RoleIs(Nel.of(MemberRole.Owner))
      val allQuery: FieldTerm.RoleIs =
        FieldTerm.RoleIs(Nel.of(MemberRole.Member, MemberRole.Owner, MemberRole.Member))
      role match
        case SearchRole.Admin(_) =>
          assertEquals(
            encode(memberQuery),
            SolrQuery(SolrToken.empty)
          )
          assertEquals(
            encode(ownerQuery),
            SolrQuery(SolrToken.empty)
          )
          assertEquals(
            encode(allQuery),
            SolrQuery(SolrToken.empty)
          )
        case SearchRole.Anonymous =>
          assertEquals(
            encode(memberQuery),
            SolrQuery(SolrToken.publicOnly)
          )
          assertEquals(
            encode(ownerQuery),
            SolrQuery(SolrToken.publicOnly)
          )
          assertEquals(
            encode(allQuery),
            SolrQuery(SolrToken.publicOnly)
          )
        case SearchRole.User(id) =>
          assertEquals(
            encode(memberQuery),
            SolrQuery(SolrToken.roleIs(id, MemberRole.Member))
          )
          assertEquals(
            encode(ownerQuery),
            SolrQuery(SolrToken.roleIs(id, MemberRole.Owner))
          )
          assertEquals(
            encode(allQuery),
            SolrQuery(SolrToken.roleIn(id, Nel.of(MemberRole.Member, MemberRole.Owner)))
          )
  }
