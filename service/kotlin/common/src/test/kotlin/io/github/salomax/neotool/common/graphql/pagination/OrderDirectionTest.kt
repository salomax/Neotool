package io.github.salomax.neotool.common.graphql.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OrderDirection Tests")
class OrderDirectionTest {
    @Nested
    @DisplayName("Enum Values")
    inner class EnumValuesTests {
        @Test
        fun `should have ASC value`() {
            // Assert
            assertThat(OrderDirection.ASC).isNotNull
            assertThat(OrderDirection.ASC.name).isEqualTo("ASC")
        }

        @Test
        fun `should have DESC value`() {
            // Assert
            assertThat(OrderDirection.DESC).isNotNull
            assertThat(OrderDirection.DESC.name).isEqualTo("DESC")
        }

        @Test
        fun `should have exactly two enum values`() {
            // Act
            val values = OrderDirection.values()

            // Assert
            assertThat(values).hasSize(2)
            assertThat(values).containsExactly(OrderDirection.ASC, OrderDirection.DESC)
        }
    }

    @Nested
    @DisplayName("Enum Comparison")
    inner class EnumComparisonTests {
        @Test
        fun `should compare enum values correctly`() {
            // Assert
            assertThat(OrderDirection.ASC).isEqualTo(OrderDirection.ASC)
            assertThat(OrderDirection.DESC).isEqualTo(OrderDirection.DESC)
            assertThat(OrderDirection.ASC).isNotEqualTo(OrderDirection.DESC)
        }

        @Test
        fun `should use enum in when expressions`() {
            // Act
            val ascResult =
                when (OrderDirection.ASC) {
                    OrderDirection.ASC -> "ascending"
                    OrderDirection.DESC -> "descending"
                }
            val descResult =
                when (OrderDirection.DESC) {
                    OrderDirection.ASC -> "ascending"
                    OrderDirection.DESC -> "descending"
                }

            // Assert
            assertThat(ascResult).isEqualTo("ascending")
            assertThat(descResult).isEqualTo("descending")
        }
    }
}
