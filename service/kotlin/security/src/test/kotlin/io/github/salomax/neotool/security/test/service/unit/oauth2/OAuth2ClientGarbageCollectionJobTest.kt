package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.job.oauth2.OAuth2ClientGarbageCollectionJob
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@DisplayName("OAuth2ClientGarbageCollectionJob Unit Tests")
class OAuth2ClientGarbageCollectionJobTest {
    @Test
    fun `sweep marks only repository-selected unused expired clients as deleted`() {
        val repository: OAuth2ClientRepository = mock()
        whenever(repository.markUnusedClientsDeleted(any(), any())).thenReturn(2)
        val job =
            OAuth2ClientGarbageCollectionJob(
                repository,
                OAuthConfig(
                    authorizationServer =
                        OAuthConfig.AuthorizationServerConfig(
                            registration =
                                OAuthConfig.AuthorizationServerConfig.RegistrationConfig(
                                    garbageCollection =
                                        OAuthConfig.AuthorizationServerConfig.RegistrationConfig
                                            .GarbageCollectionConfig(unusedTtlSeconds = 30),
                                ),
                        ),
                ),
            )

        job.sweep()

        verify(repository).markUnusedClientsDeleted(any<Instant>(), any())
    }
}
