package io.github.salomax.neotool.mcp.security

import io.micronaut.context.annotation.Context
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener

@Context
class McpResourceConfigValidator : BeanCreatedEventListener<McpResourceConfig> {
    override fun onCreated(event: BeanCreatedEvent<McpResourceConfig>): McpResourceConfig {
        event.bean.metadata.validate()
        return event.bean
    }
}
