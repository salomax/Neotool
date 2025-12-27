package io.github.salomax.neotool.security.test.repository

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.service.PrincipalType
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = false)
@DisplayName("PrincipalRepository Integration Tests")
class PrincipalRepositoryTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Test
    fun `should find principal by type and external ID`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val principal =
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId.toString(),
                enabled = true,
            )
        val saved = principalRepository.save(principal)

        // Act
        val found =
            principalRepository.findByPrincipalTypeAndExternalId(
                PrincipalType.SERVICE,
                serviceId.toString(),
            )

        // Assert
        assertThat(found).isPresent
        assertThat(found.get().id).isEqualTo(saved.id)
        assertThat(found.get().principalType).isEqualTo(PrincipalType.SERVICE)
        assertThat(found.get().externalId).isEqualTo(serviceId.toString())
        assertThat(found.get().enabled).isTrue()
    }

    @Test
    fun `should find all principals by type`() {
        // Arrange
        val serviceId1 = UUID.randomUUID()
        val serviceId2 = UUID.randomUUID()
        principalRepository.save(
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId1.toString(),
                enabled = true,
            ),
        )
        principalRepository.save(
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId2.toString(),
                enabled = true,
            ),
        )

        // Act
        val services = principalRepository.findByPrincipalType(PrincipalType.SERVICE)

        // Assert
        assertThat(services.size).isGreaterThanOrEqualTo(2)
        assertThat(services).anyMatch { it.externalId == serviceId1.toString() }
        assertThat(services).anyMatch { it.externalId == serviceId2.toString() }
    }

    @Test
    fun `should save principal with enabled flag`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val principal =
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId.toString(),
                // Disabled principal
                enabled = false,
            )

        // Act
        val saved = principalRepository.save(principal)

        // Assert
        assertThat(saved.id).isNotNull()
        assertThat(saved.enabled).isFalse()

        // Verify we can retrieve it
        val found = principalRepository.findById(saved.id!!)
        assertThat(found).isPresent
        assertThat(found.get().enabled).isFalse()
    }
}
