package io.github.salomax.neotool.common.graphql.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@DisplayName("ConnectionBuilder Tests")
class ConnectionBuilderTest {
    @Nested
    @DisplayName("buildConnection()")
    inner class BuildConnectionTests {
        @Test
        fun `should build connection with empty list`() {
            // Arrange
            val items = emptyList<String>()
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                )

            // Assert
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
            assertThat(result.pageInfo.startCursor).isNull()
            assertThat(result.pageInfo.endCursor).isNull()
            assertThat(result.totalCount).isNull()
        }

        @Test
        fun `should build connection with empty list and totalCount`() {
            // Arrange
            val items = emptyList<String>()
            val hasMore = false
            val totalCount = 0L

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                    totalCount = totalCount,
                )

            // Assert
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
            assertThat(result.totalCount).isEqualTo(totalCount)
        }

        @Test
        fun `should build connection with single item`() {
            // Arrange
            val items = listOf("item1")
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                )

            // Assert
            assertThat(result.edges).hasSize(1)
            assertThat(result.edges[0].node).isEqualTo("item1")
            assertThat(result.edges[0].cursor).isEqualTo("cursor-item1")
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
            assertThat(result.pageInfo.startCursor).isEqualTo("cursor-item1")
            assertThat(result.pageInfo.endCursor).isEqualTo("cursor-item1")
        }

        @Test
        fun `should build connection with multiple items`() {
            // Arrange
            val items = listOf("item1", "item2", "item3")
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                )

            // Assert
            assertThat(result.edges).hasSize(3)
            assertThat(result.edges[0].node).isEqualTo("item1")
            assertThat(result.edges[0].cursor).isEqualTo("cursor-item1")
            assertThat(result.edges[1].node).isEqualTo("item2")
            assertThat(result.edges[1].cursor).isEqualTo("cursor-item2")
            assertThat(result.edges[2].node).isEqualTo("item3")
            assertThat(result.edges[2].cursor).isEqualTo("cursor-item3")
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
            assertThat(result.pageInfo.startCursor).isEqualTo("cursor-item1")
            assertThat(result.pageInfo.endCursor).isEqualTo("cursor-item3")
        }

        @Test
        fun `should set hasNextPage to true when hasMore is true`() {
            // Arrange
            val items = listOf("item1", "item2")
            val hasMore = true

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                )

            // Assert
            assertThat(result.pageInfo.hasNextPage).isTrue
            assertThat(result.pageInfo.hasPreviousPage).isFalse
        }

        @Test
        fun `should set hasNextPage to false when hasMore is false`() {
            // Arrange
            val items = listOf("item1", "item2")
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                )

            // Assert
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
        }

        @Test
        fun `should include totalCount when provided`() {
            // Arrange
            val items = listOf("item1", "item2")
            val hasMore = false
            val totalCount = 100L

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = hasMore,
                    encodeCursor = { "cursor-$it" },
                    totalCount = totalCount,
                )

            // Assert
            assertThat(result.totalCount).isEqualTo(totalCount)
        }

        @Test
        fun `should use custom encodeCursor function`() {
            // Arrange
            data class TestItem(val id: UUID, val name: String)
            val uuid = UUID.randomUUID()
            val items = listOf(TestItem(uuid, "Test"))

            // Act
            val result =
                ConnectionBuilder.buildConnection(
                    items = items,
                    hasMore = false,
                    encodeCursor = { CursorEncoder.encodeCursor(it.id) },
                )

            // Assert
            assertThat(result.edges).hasSize(1)
            assertThat(result.edges[0].node).isEqualTo(items[0])
            assertThat(result.edges[0].cursor).isEqualTo(CursorEncoder.encodeCursor(uuid))
        }
    }

    @Nested
    @DisplayName("buildConnectionWithUuid()")
    inner class BuildConnectionWithUuidTests {
        @Test
        fun `should build connection with UUID items`() {
            // Arrange
            data class TestItem(val id: UUID, val name: String)
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()
            val items =
                listOf(
                    TestItem(uuid1, "Item1"),
                    TestItem(uuid2, "Item2"),
                )
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithUuid(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                )

            // Assert
            assertThat(result.edges).hasSize(2)
            assertThat(result.edges[0].node).isEqualTo(items[0])
            assertThat(result.edges[0].cursor).isEqualTo(CursorEncoder.encodeCursor(uuid1))
            assertThat(result.edges[1].node).isEqualTo(items[1])
            assertThat(result.edges[1].cursor).isEqualTo(CursorEncoder.encodeCursor(uuid2))
            assertThat(result.pageInfo.startCursor).isEqualTo(CursorEncoder.encodeCursor(uuid1))
            assertThat(result.pageInfo.endCursor).isEqualTo(CursorEncoder.encodeCursor(uuid2))
        }

        @Test
        fun `should throw IllegalArgumentException when item has null ID`() {
            // Arrange
            data class TestItem(val id: UUID?, val name: String)
            val items = listOf(TestItem(null, "Item1"))
            val hasMore = false

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    ConnectionBuilder.buildConnectionWithUuid(
                        items = items,
                        hasMore = hasMore,
                        getId = { it.id },
                    )
                }
            assertThat(exception.message).isEqualTo("Item must have a non-null ID")
        }

        @Test
        fun `should include totalCount when provided`() {
            // Arrange
            data class TestItem(val id: UUID, val name: String)
            val uuid = UUID.randomUUID()
            val items = listOf(TestItem(uuid, "Item1"))
            val hasMore = false
            val totalCount = 50L

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithUuid(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                    totalCount = totalCount,
                )

            // Assert
            assertThat(result.totalCount).isEqualTo(totalCount)
        }

        @Test
        fun `should set hasNextPage based on hasMore parameter`() {
            // Arrange
            data class TestItem(val id: UUID, val name: String)
            val uuid = UUID.randomUUID()
            val items = listOf(TestItem(uuid, "Item1"))

            // Act
            val resultWithMore =
                ConnectionBuilder.buildConnectionWithUuid(
                    items = items,
                    hasMore = true,
                    getId = { it.id },
                )
            val resultWithoutMore =
                ConnectionBuilder.buildConnectionWithUuid(
                    items = items,
                    hasMore = false,
                    getId = { it.id },
                )

            // Assert
            assertThat(resultWithMore.pageInfo.hasNextPage).isTrue
            assertThat(resultWithoutMore.pageInfo.hasNextPage).isFalse
        }

        @Test
        fun `should handle empty list`() {
            // Arrange
            data class TestItem(val id: UUID, val name: String)
            val items = emptyList<TestItem>()
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithUuid(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                )

            // Assert
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
        }
    }

    @Nested
    @DisplayName("buildConnectionWithInt()")
    inner class BuildConnectionWithIntTests {
        @Test
        fun `should build connection with Int items`() {
            // Arrange
            data class TestItem(val id: Int, val name: String)
            val items =
                listOf(
                    TestItem(1, "Item1"),
                    TestItem(2, "Item2"),
                )
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                )

            // Assert
            assertThat(result.edges).hasSize(2)
            assertThat(result.edges[0].node).isEqualTo(items[0])
            assertThat(result.edges[0].cursor).isEqualTo(CursorEncoder.encodeCursor(1))
            assertThat(result.edges[1].node).isEqualTo(items[1])
            assertThat(result.edges[1].cursor).isEqualTo(CursorEncoder.encodeCursor(2))
            assertThat(result.pageInfo.startCursor).isEqualTo(CursorEncoder.encodeCursor(1))
            assertThat(result.pageInfo.endCursor).isEqualTo(CursorEncoder.encodeCursor(2))
        }

        @Test
        fun `should throw IllegalArgumentException when item has null ID`() {
            // Arrange
            data class TestItem(val id: Int?, val name: String)
            val items = listOf(TestItem(null, "Item1"))
            val hasMore = false

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    ConnectionBuilder.buildConnectionWithInt(
                        items = items,
                        hasMore = hasMore,
                        getId = { it.id },
                    )
                }
            assertThat(exception.message).isEqualTo("Item must have a non-null ID")
        }

        @Test
        fun `should include totalCount when provided`() {
            // Arrange
            data class TestItem(val id: Int, val name: String)
            val items = listOf(TestItem(1, "Item1"))
            val hasMore = false
            val totalCount = 50L

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                    totalCount = totalCount,
                )

            // Assert
            assertThat(result.totalCount).isEqualTo(totalCount)
        }

        @Test
        fun `should set hasNextPage based on hasMore parameter`() {
            // Arrange
            data class TestItem(val id: Int, val name: String)
            val items = listOf(TestItem(1, "Item1"))

            // Act
            val resultWithMore =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = true,
                    getId = { it.id },
                )
            val resultWithoutMore =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = false,
                    getId = { it.id },
                )

            // Assert
            assertThat(resultWithMore.pageInfo.hasNextPage).isTrue
            assertThat(resultWithoutMore.pageInfo.hasNextPage).isFalse
        }

        @Test
        fun `should handle empty list`() {
            // Arrange
            data class TestItem(val id: Int, val name: String)
            val items = emptyList<TestItem>()
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                )

            // Assert
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse
            assertThat(result.pageInfo.hasPreviousPage).isFalse
        }

        @Test
        fun `should handle zero Int ID`() {
            // Arrange
            data class TestItem(val id: Int, val name: String)
            val items = listOf(TestItem(0, "Item1"))
            val hasMore = false

            // Act
            val result =
                ConnectionBuilder.buildConnectionWithInt(
                    items = items,
                    hasMore = hasMore,
                    getId = { it.id },
                )

            // Assert
            assertThat(result.edges).hasSize(1)
            assertThat(result.edges[0].cursor).isEqualTo(CursorEncoder.encodeCursor(0))
        }
    }
}
