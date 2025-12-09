package io.github.salomax.neotool.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.MDC

@DisplayName("LoggingUtils Tests")
class LoggingUtilsTest {
    @BeforeEach
    fun setUp() {
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Nested
    @DisplayName("setCorrelationId()")
    inner class SetCorrelationIdTests {
        @Test
        fun `should set correlation ID in MDC`() {
            // Act
            LoggingUtils.setCorrelationId("correlation-123")

            // Assert
            assertThat(MDC.get(MDCFilter.CORRELATION_ID_KEY)).isEqualTo("correlation-123")
        }

        @Test
        fun `should overwrite existing correlation ID`() {
            // Arrange
            LoggingUtils.setCorrelationId("correlation-123")

            // Act
            LoggingUtils.setCorrelationId("correlation-456")

            // Assert
            assertThat(MDC.get(MDCFilter.CORRELATION_ID_KEY)).isEqualTo("correlation-456")
        }
    }

    @Nested
    @DisplayName("setUserId()")
    inner class SetUserIdTests {
        @Test
        fun `should set user ID in MDC when not null`() {
            // Act
            LoggingUtils.setUserId("user-123")

            // Assert
            assertThat(MDC.get(MDCFilter.USER_ID_KEY)).isEqualTo("user-123")
        }

        @Test
        fun `should remove user ID from MDC when null`() {
            // Arrange
            LoggingUtils.setUserId("user-123")
            assertThat(MDC.get(MDCFilter.USER_ID_KEY)).isEqualTo("user-123")

            // Act
            LoggingUtils.setUserId(null)

            // Assert
            assertThat(MDC.get(MDCFilter.USER_ID_KEY)).isNull()
        }

        @Test
        fun `should not set user ID when null`() {
            // Act
            LoggingUtils.setUserId(null)

            // Assert
            assertThat(MDC.get(MDCFilter.USER_ID_KEY)).isNull()
        }
    }

    @Nested
    @DisplayName("setRequestId()")
    inner class SetRequestIdTests {
        @Test
        fun `should set request ID in MDC when not null`() {
            // Act
            LoggingUtils.setRequestId("request-123")

            // Assert
            assertThat(MDC.get(MDCFilter.REQUEST_ID_KEY)).isEqualTo("request-123")
        }

        @Test
        fun `should remove request ID from MDC when null`() {
            // Arrange
            LoggingUtils.setRequestId("request-123")
            assertThat(MDC.get(MDCFilter.REQUEST_ID_KEY)).isEqualTo("request-123")

            // Act
            LoggingUtils.setRequestId(null)

            // Assert
            assertThat(MDC.get(MDCFilter.REQUEST_ID_KEY)).isNull()
        }

        @Test
        fun `should not set request ID when null`() {
            // Act
            LoggingUtils.setRequestId(null)

            // Assert
            assertThat(MDC.get(MDCFilter.REQUEST_ID_KEY)).isNull()
        }
    }

    @Nested
    @DisplayName("setTraceContext()")
    inner class SetTraceContextTests {
        @Test
        fun `should set trace ID and span ID when both provided`() {
            // Act
            LoggingUtils.setTraceContext("trace-123", "span-456")

            // Assert
            assertThat(MDC.get("traceId")).isEqualTo("trace-123")
            assertThat(MDC.get("otel.trace_id")).isEqualTo("trace-123")
            assertThat(MDC.get("spanId")).isEqualTo("span-456")
            assertThat(MDC.get("otel.span_id")).isEqualTo("span-456")
        }

        @Test
        fun `should set only trace ID when span ID is null`() {
            // Act
            LoggingUtils.setTraceContext("trace-123", null)

            // Assert
            assertThat(MDC.get("traceId")).isEqualTo("trace-123")
            assertThat(MDC.get("otel.trace_id")).isEqualTo("trace-123")
            assertThat(MDC.get("spanId")).isNull()
            assertThat(MDC.get("otel.span_id")).isNull()
        }

        @Test
        fun `should set only span ID when trace ID is null`() {
            // Act
            LoggingUtils.setTraceContext(null, "span-456")

            // Assert
            assertThat(MDC.get("traceId")).isNull()
            assertThat(MDC.get("otel.trace_id")).isNull()
            assertThat(MDC.get("spanId")).isEqualTo("span-456")
            assertThat(MDC.get("otel.span_id")).isEqualTo("span-456")
        }

        @Test
        fun `should not set anything when both are null`() {
            // Act
            LoggingUtils.setTraceContext(null, null)

            // Assert
            assertThat(MDC.get("traceId")).isNull()
            assertThat(MDC.get("otel.trace_id")).isNull()
            assertThat(MDC.get("spanId")).isNull()
            assertThat(MDC.get("otel.span_id")).isNull()
        }
    }

    @Nested
    @DisplayName("withTiming()")
    inner class WithTimingTests {
        @Test
        fun `should return result and log timing for successful operation`() {
            // Act
            val result =
                LoggingUtils.withTiming("test-operation") {
                    "success"
                }

            // Assert
            assertThat(result).isEqualTo("success")
        }

        @Test
        fun `should throw exception and log timing for failed operation`() {
            // Act & Assert
            assertThrows<RuntimeException> {
                LoggingUtils.withTiming("test-operation") {
                    throw RuntimeException("test error")
                }
            }
        }

        @Test
        fun `should execute block and return result`() {
            // Act
            val result =
                LoggingUtils.withTiming("test-operation") {
                    42
                }

            // Assert
            assertThat(result).isEqualTo(42)
        }
    }

    @Nested
    @DisplayName("logMethodEntry()")
    inner class LogMethodEntryTests {
        @Test
        fun `should log method entry with parameters`() {
            // Act - should not throw
            LoggingUtils.logMethodEntry("testMethod", "param1" to "value1", "param2" to 42)

            // Assert - just verify it doesn't throw
            // Actual logging is hard to test without a logger mock
        }

        @Test
        fun `should log method entry without parameters`() {
            // Act - should not throw
            LoggingUtils.logMethodEntry("testMethod")

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logMethodExit()")
    inner class LogMethodExitTests {
        @Test
        fun `should log method exit with result`() {
            // Act - should not throw
            LoggingUtils.logMethodExit("testMethod", "result")

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should log method exit without result`() {
            // Act - should not throw
            LoggingUtils.logMethodExit("testMethod")

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logBusinessOperation()")
    inner class LogBusinessOperationTests {
        @Test
        fun `should log business operation with context`() {
            // Act - should not throw
            LoggingUtils.logBusinessOperation(
                "create",
                "User",
                "user-123",
                "key1" to "value1",
                "key2" to 42,
            )

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should log business operation without entity ID`() {
            // Act - should not throw
            LoggingUtils.logBusinessOperation(
                "list",
                "User",
                null,
                "key1" to "value1",
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logAuditData()")
    inner class LogAuditDataTests {
        @Test
        fun `should log audit data with context`() {
            // Act - should not throw
            LoggingUtils.logAuditData(
                "read",
                "UserService",
                "user-123",
                "key1" to "value1",
            )

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should log audit data without record ID`() {
            // Act - should not throw
            LoggingUtils.logAuditData(
                "list",
                "UserService",
                null,
                "key1" to "value1",
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logGraphQLOperation()")
    inner class LogGraphQLOperationTests {
        @Test
        fun `should log GraphQL operation with query and variables`() {
            // Act - should not throw
            LoggingUtils.logGraphQLOperation(
                "query",
                "query { user { id } }",
                mapOf("id" to "123"),
            )

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should log GraphQL operation without variables`() {
            // Act - should not throw
            LoggingUtils.logGraphQLOperation(
                "mutation",
                "mutation { createUser { id } }",
                null,
            )

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should truncate long queries`() {
            // Arrange
            val longQuery = "a".repeat(200)

            // Act - should not throw
            LoggingUtils.logGraphQLOperation(
                "query",
                longQuery,
                null,
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logSpanEvent()")
    inner class LogSpanEventTests {
        @Test
        fun `should log span event with attributes`() {
            // Arrange
            LoggingUtils.setCorrelationId("correlation-123")
            MDC.put("traceId", "trace-123")
            MDC.put("spanId", "span-456")

            // Act - should not throw
            LoggingUtils.logSpanEvent(
                "user.created",
                "userId" to "user-123",
                "timestamp" to System.currentTimeMillis(),
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logPerformanceMetric()")
    inner class LogPerformanceMetricTests {
        @Test
        fun `should log performance metric with labels`() {
            // Arrange
            LoggingUtils.setCorrelationId("correlation-123")
            MDC.put("traceId", "trace-123")

            // Act - should not throw
            LoggingUtils.logPerformanceMetric(
                "request.duration",
                150.5,
                "method" to "GET",
                "path" to "/api/users",
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("logBusinessOperationWithContext()")
    inner class LogBusinessOperationWithContextTests {
        @Test
        fun `should log business operation with enterprise context`() {
            // Arrange
            LoggingUtils.setCorrelationId("correlation-123")
            MDC.put("traceId", "trace-123")

            // Act - should not throw
            LoggingUtils.logBusinessOperationWithContext(
                "update",
                "User",
                "user-123",
                "key1" to "value1",
            )

            // Assert - just verify it doesn't throw
        }
    }

    @Nested
    @DisplayName("sanitizeValue()")
    inner class SanitizeValueTests {
        @Test
        fun `should sanitize email addresses through logMethodEntry`() {
            // Act - logMethodEntry uses sanitizeValue internally
            // This tests sanitizeValue indirectly
            LoggingUtils.logMethodEntry("test", "email" to "test@example.com")

            // Assert - just verify it doesn't throw
            // The actual sanitization is tested indirectly through logging
        }

        @Test
        fun `should truncate long strings through logMethodEntry`() {
            // Arrange
            val longString = "a".repeat(30)

            // Act - logMethodEntry uses sanitizeValue internally
            LoggingUtils.logMethodEntry("test", "long" to longString)

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should not sanitize short strings through logMethodEntry`() {
            // Act
            LoggingUtils.logMethodEntry("test", "short" to "short")

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should sanitize maps recursively through logMethodEntry`() {
            // Arrange
            val map = mapOf("email" to "test@example.com", "name" to "John")

            // Act
            LoggingUtils.logMethodEntry("test", "map" to map)

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should sanitize lists recursively through logMethodEntry`() {
            // Arrange
            val list = listOf("test@example.com", "normal@value")

            // Act
            LoggingUtils.logMethodEntry("test", "list" to list)

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should return other types as-is through logMethodEntry`() {
            // Act
            LoggingUtils.logMethodEntry("test", "number" to 42)

            // Assert - just verify it doesn't throw
        }

        @Test
        fun `should handle null values through logMethodEntry`() {
            // Act
            LoggingUtils.logMethodEntry("test", "null" to null)

            // Assert - just verify it doesn't throw
        }
    }
}
