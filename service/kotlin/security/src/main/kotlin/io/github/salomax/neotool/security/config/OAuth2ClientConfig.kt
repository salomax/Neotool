package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.EachProperty

@EachProperty(value = "oauth.authorization-server.clients", list = true)
class OAuth2ClientConfig {
    var clientId: String = ""
    var clientName: String = ""
    var redirectUris: List<String> = emptyList()
    var scopes: List<String> = emptyList()
    var resources: List<String> = emptyList()
}
