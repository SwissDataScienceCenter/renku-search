package io.renku.search.authzed

import fs2.Stream
import cats.effect.*
import scala.jdk.CollectionConverters.*
import cats.syntax.all.*
import com.authzed.api.v1.{
  CheckPermissionRequest as CPR,
  Consistency,
  Cursor,
  LookupResourcesRequest as LRR,
  PermissionsServiceGrpc,
  SubjectReference
}
import com.authzed.grpcutil.BearerToken as GrpcBearerToken

trait PermissionService[F[_]]:
  def checkPermission(req: CheckPermissionRequest): F[String]
  def lookupResources(req: LookupResourceRequest): Stream[F, String]

object PermissionService:

  def apply[F[_]: Sync](
      channel: ManagedChannel[F],
      token: BearerToken
  ): PermissionService[F] =
    val bt = new GrpcBearerToken(token.value)
    val svc = PermissionsServiceGrpc
      .newBlockingStub(channel.underlying)
      .withCallCredentials(bt);
    new Impl[F](svc)

  private class Impl[F[_]: Sync](
      svc: PermissionsServiceGrpc.PermissionsServiceBlockingStub
  ) extends PermissionService[F] {
    def checkPermission(req: CheckPermissionRequest): F[String] = {
      val r = CPR
        .newBuilder()
        .setConsistency(Consistency.newBuilder().setMinimizeLatency(true).build())
        .setResource(req.objectRef.toUnderlying)
        .setSubject(SubjectReference.newBuilder().setObject(req.subjectRef.toUnderlying))
        .setPermission(req.permission)
        .build()

      Sync[F].blocking(svc.checkPermission(r)).map(_.getPermissionship.toString)
    }

    def lookupResources(req: LookupResourceRequest): Stream[F, String] = {
      val r = LRR
        .newBuilder()
        .setConsistency(Consistency.newBuilder().setMinimizeLatency(true).build())
        .setPermission(req.permission)
        .setResourceObjectType(req.resourceType)
        .setSubject(SubjectReference.newBuilder().setObject(req.subject.toUnderlying))

      req.cursor.foreach(c =>
        r.setOptionalCursor(Cursor.newBuilder().setToken(c).build())
      )
      req.limit.foreach(l => r.setOptionalLimit(l))

      Stream
        .eval(Sync[F].blocking(svc.lookupResources(r.build())))
        .flatMap(iter => Stream.fromBlockingIterator(iter.asScala, 1024))
        .map(_.getResourceObjectId)
    }
  }
