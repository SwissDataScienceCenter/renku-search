package io.renku.search.model

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.syntax.all.*

import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

object ModelGenerators:
  val idGen: Gen[Id] = Gen.uuid.map(uuid => Id(uuid.toString))
  val nameGen: Gen[Name] =
    alphaStringGen(max = 10).map(Name.apply)
  val projectDescGen: Gen[Description] =
    alphaStringGen(max = 30).map(Description.apply)

  val timestampGen: Gen[Timestamp] =
    Gen
      .choose(
        Instant.parse("2020-01-01T01:00:00Z").toEpochMilli,
        Instant.now().toEpochMilli
      )
      .map(millis => Timestamp(Instant.ofEpochMilli(millis)))

  val namespaceGen: Gen[Namespace] =
    alphaStringGen(max = 10).map(Namespace.apply)

  val memberRoleGen: Gen[MemberRole] =
    Gen.oneOf(MemberRole.valuesV2)

  val visibilityGen: Gen[Visibility] =
    Gen.oneOf(Visibility.values.toList)
  val creationDateGen: Gen[CreationDate] =
    instantGen().map(CreationDate.apply)

  val keywordGen: Gen[Keyword] =
    Gen
      .oneOf("geo", "science", "fs24", "test", "music", "ml", "ai", "flights", "scala")
      .map(Keyword.apply)

  val keywordsGen: Gen[List[Keyword]] =
    Gen.choose(0, 4).flatMap(n => Gen.listOfN(n, keywordGen))

  private def alphaStringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, Gen.alphaChar))

  private def instantGen(
      min: Instant = Instant.EPOCH,
      max: Instant = Instant.now()
  ): Gen[Instant] =
    Gen
      .chooseNum(min.toEpochMilli, max.toEpochMilli)
      .map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.MILLIS))

  val userFirstNameGen: Gen[FirstName] = Gen
    .oneOf("Eike", "Kuba", "Ralf", "Lorenzo", "Jean-Pierre", "Alfonso")
    .map(FirstName.apply)
  val userLastNameGen: Gen[LastName] = Gen
    .oneOf("Kowalski", "Doe", "Tourist", "Milkman", "Da Silva", "Bar")
    .map(LastName.apply)
  def userEmailGen(first: FirstName, last: LastName): Gen[Email] = Gen
    .oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch")
    .map(v => Email(s"$first.$last@$v"))
  val userEmailGen: Gen[Email] =
    (
      Gen.oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch"),
      userFirstNameGen,
      userLastNameGen
    ).mapN((f, l, p) => Email(s"$f.$l@$p"))

  val groupNameGen: Gen[Name] =
    Gen.oneOf(
      List("sdsc", "renku", "datascience", "rocket-science").map(Name.apply)
    )
  val groupDescGen: Gen[Description] =
    alphaStringGen(max = 5).map(Description.apply)
