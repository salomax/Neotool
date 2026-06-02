package io.github.salomax.neotool.common.graphql.pagination

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaginationConstants Tests")
class PaginationConstantsTest {
    @Nested
    @DisplayName("validatePageSize(first: Int?)")
    inner class ValidateNullablePageSizeTests {
        @Test
        fun `should return default page size when first is null`() {
            val pageSize = PaginationConstants.validatePageSize(null)

            assertThat(pageSize).isEqualTo(PaginationConstants.DEFAULT_PAGE_SIZE)
        }

        @Test
        fun `should return provided page size when first is in valid range`() {
            val pageSize = PaginationConstants.validatePageSize(50)

            assertThat(pageSize).isEqualTo(50)
        }

        @Test
        fun `should throw when first is less than 1`() {
            val invalidValue: Int? = 0

            assertThatThrownBy { PaginationConstants.validatePageSize(invalidValue) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("at least 1")
        }

        @Test
        fun `should throw when first is greater than max page size`() {
            val invalidValue: Int? = PaginationConstants.MAX_PAGE_SIZE + 1

            assertThatThrownBy { PaginationConstants.validatePageSize(invalidValue) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("at most ${PaginationConstants.MAX_PAGE_SIZE}")
        }
    }

    @Nested
    @DisplayName("validatePageSize(first: Int)")
    inner class ValidateNonNullPageSizeTests {
        @Test
        fun `should return provided page size when first is in valid range`() {
            val pageSize = PaginationConstants.validatePageSize(1)

            assertThat(pageSize).isEqualTo(1)
        }

        @Test
        fun `should throw when first is out of range`() {
            assertThatThrownBy { PaginationConstants.validatePageSize(0) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("between 1 and ${PaginationConstants.MAX_PAGE_SIZE}")
        }
    }
}
