package io.github.salomax.neotool.common.security.config

import io.micronaut.context.annotation.Context
import io.micronaut.context.env.Environment
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener

@Context
class JwtIssuerConfigValidator(
    private val environment: Environment,
) : BeanCreatedEventListener<JwtConfig> {
    override fun onCreated(event: BeanCreatedEvent<JwtConfig>): JwtConfig {
        event.bean.validateIssuer(environment.activeNames)
        return event.bean
    }
}
