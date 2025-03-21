package io.renku.search.solr.client

import cats.syntax.all.*

import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.*
import io.renku.search.model.ModelGenerators.*
import io.renku.search.model.Visibility
import io.renku.search.solr.documents.*
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import org.scalacheck.Gen
import org.scalacheck.Gen.const
import org.scalacheck.cats.implicits.*

object SolrDocumentGenerators extends SolrDocumentGenerators

trait SolrDocumentGenerators:

  private def idGen: Gen[Id] =
    Gen.uuid.map(uuid => Id(uuid.toString))

  val partialProjectGen: Gen[PartialEntityDocument.Project] =
    (idGen, const(DocVersion.Off), entityMembersGen)
      .mapN((id, v, mem) =>
        PartialEntityDocument.Project(id = id, version = v).setMembers(mem)
      )

  val projectDocumentGen: Gen[Project] =
    val differentiator = nameGen.generateOne
    projectDocumentGen(
      s"proj-$differentiator",
      s"proj desc $differentiator",
      userDocumentGen.asOption,
      userOrGroupDocumentGen.asOption
    )

  val projectDocumentGenForInsert: Gen[Project] =
    val differentiator = nameGen.generateOne
    projectDocumentGen(
      s"proj-$differentiator",
      s"proj desc $differentiator",
      Gen.const(None),
      Gen.const(None)
    )

  def projectDocumentGen(
      name: String,
      desc: String,
      creatorGen: Gen[Option[User]],
      namespaceGen: Gen[Option[User | Group]],
      visibilityGen: Gen[Visibility] = visibilityGen
  ): Gen[Project] =
    (idGen, idGen, namespaceGen, visibilityGen, creationDateGen, creatorGen)
      .mapN((projectId, creatorId, namespace, visibility, creationDate, creator) =>
        Project(
          id = projectId,
          version = DocVersion.NotExists,
          name = Name(name),
          slug = Slug(name),
          namespace = namespace.flatMap {
            case u: User  => u.namespace
            case g: Group => g.namespace.some
          },
          namespaceDetails = namespace.map(ResponseBody.single),
          repositories = Seq(Repository(s"http://github.com/$name")),
          visibility = visibility,
          description = Option(Description(desc)),
          createdBy = creatorId,
          creatorDetails = creator.map(_.copy(id = creatorId)).map(ResponseBody.single),
          creationDate = creationDate
        )
      )

  def userDocumentGen: Gen[User] =
    (
      idGen,
      Gen.option(userFirstNameGen),
      Gen.option(userLastNameGen),
      Gen.some(ModelGenerators.namespaceGen)
    )
      .flatMapN { case (id, f, l, ns) =>
        User.of(id, ns, f, l)
      }

  lazy val entityMembersGen: Gen[EntityMembers] =
    val ids = Gen.choose(1, 5).flatMap(n => Gen.listOfN(n, idGen)).map(_.toSet)
    (ids, ids, ids, ids)
      .mapN((own, edi, view, mem) => EntityMembers(own, edi, view, mem))

  lazy val groupDocumentGen: Gen[Group] =
    (idGen, idGen, groupNameGen, namespaceGen, Gen.option(groupDescGen))
      .mapN((groupId, creatorId, name, namespace, desc) =>
        Group(groupId, DocVersion.NotExists, name, namespace, desc)
      )

  val partialGroupGen: Gen[PartialEntityDocument.Group] =
    (idGen, idGen, groupNameGen, namespaceGen, Gen.option(groupDescGen))
      .mapN((groupId, creatorId, name, namespace, desc) =>
        PartialEntityDocument.Group(
          groupId,
          DocVersion.NotExists,
          Some(name),
          Some(namespace),
          desc
        )
      )

  def userOrGroupDocumentGen: Gen[User | Group] =
    Gen.oneOf(userDocumentGen, groupDocumentGen)
