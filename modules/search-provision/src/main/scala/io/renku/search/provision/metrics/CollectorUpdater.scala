package io.renku.search.provision.metrics

import io.renku.redis.client.QueueName

private trait CollectorUpdater[F[_]]:
  def update(queueName: QueueName): F[Unit]
