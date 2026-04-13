package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.model.preferences.UserPreferencesEntity
import io.github.salomax.neotool.security.model.preferences.UserPreferencesMapper
import io.github.salomax.neotool.security.repo.UserPreferencesRepository
import io.github.salomax.neotool.security.service.preferences.UserPreferencesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
                keepMeConnected = null,
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
                keepMeConnected = null,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `should default keepMeConnected to true for email users`() {
        val userId = UUID.randomUUID()
        whenever(repository.findById(userId)).thenReturn(Optional.empty())

        val result = service.get(userId)

        assertThat(result.keepMeConnected).isTrue()
    }

    @Test
    fun `should default keepMeConnected to true for oauth users`() {
        val userId = UUID.randomUUID()
        whenever(repository.findById(userId)).thenReturn(Optional.empty())

        val result = service.get(userId)

        assertThat(result.keepMeConnected).isTrue()
    }

    @Test
    fun `should persist keepMeConnected patch`() {
        val userId = UUID.randomUUID()
        whenever(repository.findById(userId)).thenReturn(Optional.empty())
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as UserPreferencesEntity }

        val updated =
            service.update(
                userId = userId,
                language = null,
                theme = null,
                blurBalances = null,
                firstDayOfWeek = null,
                keepMeConnected = false,
            )

        val entityCaptor = argumentCaptor<UserPreferencesEntity>()
        verify(repository).save(entityCaptor.capture())
        assertThat(entityCaptor.firstValue.keepMeConnected).isFalse()
        assertThat(updated.keepMeConnected).isFalse()
    }
}
