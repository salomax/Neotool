package io.github.salomax.neotool.mcp.ratelimit

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("mcp.rate-limit")
open class McpRateLimitConfig(
    var token: TokenConfig = TokenConfig(),
    var user: UserScopeLimitConfig = UserScopeLimitConfig(),
    var cleanup: CleanupConfig = CleanupConfig(),
) {
    @ConfigurationProperties("token")
    open class TokenConfig(
        var read: ReadScopeLimitConfig = ReadScopeLimitConfig(),
        var write: WriteScopeLimitConfig = WriteScopeLimitConfig(),
    )

    open class ScopeLimitConfig(
        var limit: Long = 0,
        var windowMs: Long = 60_000,
    )

    @ConfigurationProperties("read")
    open class ReadScopeLimitConfig : ScopeLimitConfig(limit = 60, windowMs = 60_000)

    @ConfigurationProperties("write")
    open class WriteScopeLimitConfig : ScopeLimitConfig(limit = 15, windowMs = 60_000)

    @ConfigurationProperties("user")
    open class UserScopeLimitConfig : ScopeLimitConfig(limit = 120, windowMs = 60_000)

    @ConfigurationProperties("cleanup")
    open class CleanupConfig(
        var fixedDelay: String = "PT1H",
    )
}
