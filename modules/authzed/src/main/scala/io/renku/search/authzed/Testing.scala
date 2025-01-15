package io.renku.search.authzed

import cats.effect.*

object Testing extends IOApp:

  val token = BearerToken.unsafe("dev")
  val channelCfg = ChannelConfig.plain("localhost:50051", token)
  val channelResource = ManagedChannel[IO](channelCfg)

  def run(args: List[String]): IO[ExitCode] =
    val req = LookupResourceRequest(
      "project",
      "write",
      ObjectRef.unsafeParse("user:29612522-786d-4d28-be32-5d86c21c8c2b")
    )
    channelResource.use { ch =>
      for
        result <- ch.permissionService.lookupResources(req).evalTap(IO.println).compile.drain

      yield ExitCode.Success
    }
