package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.Context
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener

@Context
class OAuthAuthorizationServerConfigValidator : BeanCreatedEventListener<OAuthConfig> {
    override fun onCreated(event: BeanCreatedEvent<OAuthConfig>): OAuthConfig {
        event.bean.authorizationServer.metadata.validate()
        return event.bean
    }
}
