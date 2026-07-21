package io.github.salomax.neotool.security.job.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

@Singleton
@Requires(
    property = "oauth.authorization-server.registration.garbage-collection.enabled",
    value = "true",
    defaultValue = "true",
)
@Requires(property = "datasources.default.enabled", notEquals = "false")
open class OAuth2ClientGarbageCollectionJob(
    private val clientRepository: OAuth2ClientRepository,
    private val oauthConfig: OAuthConfig,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${oauth.authorization-server.registration.garbage-collection.fixed-delay:PT24H}")
    fun sweep() {
        val now = Instant.now()
        val ttlSeconds = oauthConfig.authorizationServer.registration.garbageCollection.unusedTtlSeconds
        val deleted =
            clientRepository.markUnusedClientsDeleted(
                issuedBefore = now.minusSeconds(ttlSeconds),
                deletedAt = now,
            )
        if (deleted == 0L) {
            logger.debug { "OAuth2ClientGarbageCollection: no unused clients to delete" }
            return
        }
        logger.info { "OAuth2ClientGarbageCollection: marked $deleted unused client(s) as deleted" }
    }
}
