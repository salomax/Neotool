package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.model.preferences.UserPreferencesMapper
import io.github.salomax.neotool.security.repo.UserPreferencesRepository
import io.github.salomax.neotool.security.service.preferences.UserPreferencesService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("UserPreferencesService Unit Tests")
class UserPreferencesServiceTest {
    private lateinit var repository: UserPreferencesRepository
    private lateinit var mapper: UserPreferencesMapper
    private lateinit var service: UserPreferencesService

    @BeforeEach
    fun setUp() {
        repository = mock()
        mapper = UserPreferencesMapper()
        service = UserPreferencesService(repository, mapper)
        whenever(repository.findById(any())).thenReturn(Optional.empty())
    }

    @Test
    fun `should reject first day of week outside valid range`() {
        assertThrows<ValidationException> {
            service.update(
                userId = UUID.randomUUID(),
                language = null,
                theme = null,
                blurBalances = null,
                firstDayOfWeek = 0,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `should reject unknown theme`() {
        assertThrows<ValidationException> {
            service.update(
                userId = UUID.randomUUID(),
                language = null,
                theme = "SOMETHING_ELSE",
                blurBalances = null,
                firstDayOfWeek = null,
            )
        }

        verify(repository, never()).save(any())
    }
}
