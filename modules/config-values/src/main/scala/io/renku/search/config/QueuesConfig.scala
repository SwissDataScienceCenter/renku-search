package io.renku.search.config

import cats.syntax.all.*
import ciris.{ConfigValue, Effect}
import io.renku.redis.client.QueueName

final case class QueuesConfig(
    projectCreated: QueueName,
    projectUpdated: QueueName,
    projectRemoved: QueueName,
    projectAuthorizationAdded: QueueName,
    projectAuthorizationUpdated: QueueName,
    projectAuthorizationRemoved: QueueName,
    userAdded: QueueName,
    userUpdated: QueueName,
    userRemoved: QueueName,
    groupAdded: QueueName,
    groupUpdated: QueueName,
    groupRemoved: QueueName,
    groupMemberAdded: QueueName,
    groupMemberUpdated: QueueName,
    groupMemberRemoved: QueueName,
    dataServiceAllEvents: QueueName
):
  lazy val all: Set[QueueName] = Set(
    projectCreated,
    projectUpdated,
    projectRemoved,
    projectAuthorizationAdded,
    projectAuthorizationUpdated,
    projectAuthorizationRemoved,
    userAdded,
    userUpdated,
    userRemoved,
    groupAdded,
    groupUpdated,
    groupRemoved,
    groupMemberAdded,
    groupMemberUpdated,
    groupMemberRemoved,
    dataServiceAllEvents
  )

object QueuesConfig:
  def config(cv: ConfigValues): ConfigValue[Effect, QueuesConfig] =
    (
      cv.eventQueue("project_created"),
      cv.eventQueue("project_updated"),
      cv.eventQueue("project_removed"),
      cv.eventQueue("projectauth_added"),
      cv.eventQueue("projectauth_updated"),
      cv.eventQueue("projectauth_removed"),
      cv.eventQueue("user_added"),
      cv.eventQueue("user_updated"),
      cv.eventQueue("user_removed"),
      cv.eventQueue("group_added"),
      cv.eventQueue("group_updated"),
      cv.eventQueue("group_removed"),
      cv.eventQueue("groupmember_added"),
      cv.eventQueue("groupmember_updated"),
      cv.eventQueue("groupmember_removed"),
      cv.eventQueue("dataservice_allevents")
    ).mapN(QueuesConfig.apply)
