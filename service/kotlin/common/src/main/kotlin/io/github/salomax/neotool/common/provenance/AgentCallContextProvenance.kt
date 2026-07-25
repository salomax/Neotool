package io.github.salomax.neotool.common.provenance

import io.github.salomax.neotool.common.security.agent.AgentCallContext

fun AgentCallContext.toChangeOrigin(): ChangeOrigin = ChangeOrigin.mcp(originClientId, toolCallId)

fun AgentCallContext?.toChangeOriginOrUi(): ChangeOrigin = this?.toChangeOrigin() ?: ChangeOrigin.ui()
