package io.github.salomax.neotool.app.batch.swapi.test

import io.github.salomax.neotool.app.batch.swapi.PeopleMessage
import io.github.salomax.neotool.app.batch.swapi.PeoplePayload
import io.github.salomax.neotool.app.batch.swapi.PeopleProcessor
import io.github.salomax.neotool.app.batch.swapi.metrics.PeopleMetrics
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("PeopleProcessor Unit Tests")
class PeopleProcessorTest {
    private lateinit var metrics: PeopleMetrics
    private lateinit var processor: PeopleProcessor

    @BeforeEach
    fun setUp() {
        val registry: MeterRegistry = SimpleMeterRegistry()
        metrics = PeopleMetrics(registry)
        processor = PeopleProcessor(metrics)
    }

    @Nested
    @DisplayName("process()")
    inner class ProcessTests {
        @Test
        fun `should process valid message successfully`() {
            // Arrange
            val message = createValidMessage()

            // Act
            processor.process(message)

            // Assert - no exception thrown
            // In real implementation, verify database save, etc.
        }

        @Test
        fun `should throw ValidationException when batchId is blank`() {
            // Arrange
            val message = createValidMessage().copy(batchId = "")

            // Act & Assert
            // ValidationException is now thrown directly, not wrapped in ProcessingException
            assertThatThrownBy { processor.process(message) }
                .isInstanceOf(ValidationException::class.java)
        }

        @Test
        fun `should throw ValidationException when recordId is blank`() {
            // Arrange
            val message = createValidMessage().copy(recordId = "")

            // Act & Assert
            // ValidationException is now thrown directly, not wrapped in ProcessingException
            assertThatThrownBy { processor.process(message) }
                .isInstanceOf(ValidationException::class.java)
        }

        @Test
        fun `should throw ValidationException when name is blank`() {
            // Arrange
            val message =
                createValidMessage().copy(
                    payload = createValidMessage().payload.copy(name = ""),
                )

            // Act & Assert
            // ValidationException is now thrown directly, not wrapped in ProcessingException
            assertThatThrownBy { processor.process(message) }
                .isInstanceOf(ValidationException::class.java)
        }

        @Test
        fun `should throw ValidationException when ingestedAt is invalid`() {
            // Arrange
            val message = createValidMessage().copy(ingestedAt = "invalid-date")

            // Act & Assert
            // ValidationException is now thrown directly, not wrapped in ProcessingException
            assertThatThrownBy { processor.process(message) }
                .isInstanceOf(ValidationException::class.java)
        }
    }

    private fun createValidMessage(): PeopleMessage {
        return PeopleMessage(
            batchId = "test-batch-id",
            recordId = "1",
            ingestedAt = Instant.now().toString(),
            payload =
                PeoplePayload(
                    name = "Luke Skywalker",
                    height = "172",
                    mass = "77",
                    hairColor = "blond",
                    skinColor = "fair",
                    eyeColor = "blue",
                    birthYear = "19BBY",
                    gender = "male",
                    homeworldUrl = "https://swapi.dev/api/planets/1/",
                    films = listOf("https://swapi.dev/api/films/1/"),
                    species = listOf("https://swapi.dev/api/species/1/"),
                    vehicles = emptyList(),
                    starships = listOf("https://swapi.dev/api/starships/12/"),
                ),
        )
    }
}
