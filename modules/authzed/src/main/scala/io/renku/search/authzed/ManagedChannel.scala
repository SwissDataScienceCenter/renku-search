package io.renku.search.authzed

import cats.effect.*
import io.grpc.{ManagedChannel as GrpcChannel}
import java.util.concurrent.TimeUnit

trait ManagedChannel[F[_]]:
  private[authzed] def underlying: GrpcChannel

  def permissionService: PermissionService[F]

object ManagedChannel:

  def apply[F[_]: Sync](cfg: ChannelConfig): Resource[F, ManagedChannel[F]] =
    val create = Sync[F].blocking(cfg.toUnderlying.build())
    Resource
      .make(create)(c =>
        Sync[F].blocking { c.shutdown; c.awaitTermination(1, TimeUnit.MINUTES); () }
      )
      .map(new Impl[F](_, cfg.credentials))

  private class Impl[F[_]: Sync](
      private[authzed] val underlying: GrpcChannel,
      credentials: BearerToken
  ) extends ManagedChannel[F] {
    val permissionService: PermissionService[F] =
      PermissionService(this, credentials)
  }
