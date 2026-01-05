package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.model.PasswordResetAttemptEntity
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.service.rate.RateLimitService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("RateLimitService Unit Tests")
class RateLimitServiceTest {
    private lateinit var passwordResetAttemptRepository: PasswordResetAttemptRepository
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setUp() {
        passwordResetAttemptRepository = mock()
        rateLimitService = RateLimitService(passwordResetAttemptRepository)
    }

    @Nested
    @DisplayName("Rate Limiting")
    inner class RateLimitingTests {
        @Test
        fun `should allow first request`() {
            val email = "test@example.com"
            val now = Instant.now()

            whenever(
                passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(
                    eq(email),
                    any(),
                ),
            ).thenReturn(emptyList())
            whenever(passwordResetAttemptRepository.save(any())).thenAnswer {
                it.arguments[0] as PasswordResetAttemptEntity
            }

            val result = rateLimitService.isRateLimited(email)

            assertThat(result).isFalse()
            verify(passwordResetAttemptRepository).save(any())

            val captor = ArgumentCaptor.forClass(PasswordResetAttemptEntity::class.java)
            verify(passwordResetAttemptRepository).save(captor.capture())
            val savedAttempt = captor.value
            assertThat(savedAttempt.email).isEqualTo(email)
            assertThat(savedAttempt.attemptCount).isEqualTo(1)
        }

        @Test
        fun `should allow requests within limit`() {
            val email = "test@example.com"
            val now = Instant.now()
            val existingAttempt =
                PasswordResetAttemptEntity(
                    email = email,
                    attemptCount = 2,
                    windowStart = now.minus(30, ChronoUnit.MINUTES),
                )

            whenever(
                passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(
                    eq(email),
                    any(),
                ),
            ).thenReturn(listOf(existingAttempt))
            whenever(passwordResetAttemptRepository.save(any())).thenAnswer {
                it.arguments[0] as PasswordResetAttemptEntity
            }

            val result = rateLimitService.isRateLimited(email)

            assertThat(result).isFalse()
            verify(passwordResetAttemptRepository).save(any())

            val captor = ArgumentCaptor.forClass(PasswordResetAttemptEntity::class.java)
            verify(passwordResetAttemptRepository).save(captor.capture())
            val savedAttempt = captor.value
            assertThat(savedAttempt.attemptCount).isEqualTo(3)
        }

        @Test
        fun `should block request when limit exceeded`() {
            val email = "test@example.com"
            val now = Instant.now()
            val existingAttempt =
                PasswordResetAttemptEntity(
                    email = email,
                    attemptCount = 3,
                    windowStart = now.minus(30, ChronoUnit.MINUTES),
                )

            whenever(
                passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(
                    eq(email),
                    any(),
                ),
            ).thenReturn(listOf(existingAttempt))

            val result = rateLimitService.isRateLimited(email)

            assertThat(result).isTrue()
            verify(passwordResetAttemptRepository, never()).save(any())
        }

        @Test
        fun `should allow request after window expires`() {
            val email = "test@example.com"
            val now = Instant.now()
            val oldAttempt =
                PasswordResetAttemptEntity(
                    email = email,
                    attemptCount = 3,
                    // Outside window
                    windowStart = now.minus(2, ChronoUnit.HOURS),
                )

            whenever(
                passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(
                    eq(email),
                    any(),
                ),
            ).thenReturn(listOf(oldAttempt))
            whenever(passwordResetAttemptRepository.save(any())).thenAnswer {
                it.arguments[0] as PasswordResetAttemptEntity
            }

            val result = rateLimitService.isRateLimited(email)

            assertThat(result).isFalse()
            verify(passwordResetAttemptRepository).save(any())

            val captor = ArgumentCaptor.forClass(PasswordResetAttemptEntity::class.java)
            verify(passwordResetAttemptRepository).save(captor.capture())
            val savedAttempt = captor.value
            assertThat(savedAttempt.attemptCount).isEqualTo(1) // New attempt
        }

        @Test
        fun `should create new attempt when no existing attempts found`() {
            val email = "test@example.com"

            whenever(
                passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(
                    eq(email),
                    any(),
                ),
            ).thenReturn(emptyList())
            whenever(passwordResetAttemptRepository.save(any())).thenAnswer {
                it.arguments[0] as PasswordResetAttemptEntity
            }

            val result = rateLimitService.isRateLimited(email)

            assertThat(result).isFalse()
            verify(passwordResetAttemptRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Cleanup")
    inner class CleanupTests {
        @Test
        fun `should cleanup old attempts`() {
            val oldAttempts =
                listOf(
                    PasswordResetAttemptEntity(
                        email = "test1@example.com",
                        attemptCount = 1,
                        windowStart = Instant.now().minus(25, ChronoUnit.HOURS),
                    ),
                    PasswordResetAttemptEntity(
                        email = "test2@example.com",
                        attemptCount = 2,
                        windowStart = Instant.now().minus(26, ChronoUnit.HOURS),
                    ),
                )

            whenever(passwordResetAttemptRepository.findByCreatedAtLessThan(any()))
                .thenReturn(oldAttempts)
            doNothing().whenever(passwordResetAttemptRepository).deleteAll(oldAttempts)

            rateLimitService.cleanupOldAttempts()

            verify(passwordResetAttemptRepository).findByCreatedAtLessThan(any())
            verify(passwordResetAttemptRepository).deleteAll(oldAttempts)
        }

        @Test
        fun `should handle empty cleanup gracefully`() {
            whenever(passwordResetAttemptRepository.findByCreatedAtLessThan(any()))
                .thenReturn(emptyList())

            rateLimitService.cleanupOldAttempts()

            verify(passwordResetAttemptRepository).findByCreatedAtLessThan(any())
            verify(passwordResetAttemptRepository, never()).deleteAll(any())
        }
    }
}
