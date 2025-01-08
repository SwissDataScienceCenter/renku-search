package io.renku.search.events

import cats.data.NonEmptyList

import io.renku.search.model.Id
import org.apache.avro.Schema

trait RenkuEventPayload:
  def id: Id
  def version: NonEmptyList[SchemaVersion]
  def schema: Schema
  def msgType: MsgType
