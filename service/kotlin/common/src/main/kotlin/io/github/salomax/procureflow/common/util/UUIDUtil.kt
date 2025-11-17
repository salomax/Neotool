package io.github.salomax.procureflow.common.util

import java.util.UUID

fun toUUID(uuid: Any?): UUID =
  uuid.let { UUID.fromString(it.toString()) }
