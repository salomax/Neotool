package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.key.FileKeyManager
import io.github.salomax.neotool.common.security.key.JwksClient
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Singleton

@Factory
class McpKeyManagerFactory {
    @Singleton
    @Replaces(FileKeyManager::class)
    fun fileKeyManager(
        jwtConfig: JwtConfig,
        jwksClient: JwksClient,
    ): FileKeyManager = FileKeyManager(jwtConfig, jwksClient)
}
