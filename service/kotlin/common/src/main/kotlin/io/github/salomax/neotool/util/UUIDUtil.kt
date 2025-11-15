package io.github.salomax.neotool.framework.util

import java.util.UUID

fun toUUID(uuid: Any?): UUID =
  uuid.let { UUID.fromString(it.toString()) }
