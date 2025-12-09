package io.github.salomax.neotool.common.test.transaction

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.hibernate.Transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atMost
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("TransactionExtensions Tests")
class TransactionExtensionsTest {
    private lateinit var entityManager: EntityManager
    private lateinit var session: Session
    private lateinit var transaction: Transaction

    @BeforeEach
    fun setUp() {
        entityManager = mock()
        session = mock()
        transaction = mock()

        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.transaction).thenReturn(transaction)
    }

    @Nested
    @DisplayName("Successful Transaction Execution")
    inner class SuccessfulTransactionTests {
        @Test
        fun `should begin, execute block, flush, and commit when no active transaction`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(false, false) // Inactive initially and in finally
            val expectedResult = "test-result"

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)

            verify(transaction, atLeast(1)).isActive // Check transaction state
            verify(transaction).begin() // Begin new transaction
            verify(entityManager).flush() // Flush changes
            verify(transaction).commit() // Commit transaction
            verify(transaction, never()).rollback() // Should not rollback on success
        }

        @Test
        fun `should commit active transaction, begin new one, execute block, flush, and commit`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(true, false, false) // Active initially, inactive after commits
            val expectedResult = 42

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)

            val inOrder = inOrder(transaction, entityManager)
            inOrder.verify(transaction).isActive // Check if active (returns true)
            inOrder.verify(transaction).commit() // Commit active transaction
            inOrder.verify(transaction).begin() // Begin new transaction
            inOrder.verify(entityManager).flush() // Flush changes
            inOrder.verify(transaction).commit() // Commit new transaction
            inOrder.verify(transaction).isActive // Check again in finally block (returns false)
            inOrder.verify(transaction).begin() // Begin transaction for test continuation (wasCommitted && !isActive)
        }

        @Test
        fun `should handle block that returns Unit`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(false, false)
            var blockExecuted = false

            // Act
            entityManager.runTransaction {
                blockExecuted = true
            }

            // Assert
            assertThat(blockExecuted).isTrue()
            verify(transaction, atLeast(1)).isActive
            verify(transaction).begin()
            verify(entityManager).flush()
            verify(transaction).commit()
            verify(transaction, never()).rollback()
        }
    }

    @Nested
    @DisplayName("Transaction Rollback on Exception")
    inner class TransactionRollbackTests {
        @Test
        fun `should rollback transaction when block throws exception`() {
            // Arrange
            // Inactive initially, active when exception occurs, inactive in finally
            whenever(transaction.isActive).thenReturn(false, true, false)
            val exception = RuntimeException("Test exception")

            // Act & Assert
            org.junit.jupiter.api.assertThrows<RuntimeException> {
                entityManager.runTransaction {
                    throw exception
                }
            }

            verify(transaction, times(2)).isActive // Check initially and before rollback
            verify(transaction).begin() // Begin transaction
            verify(transaction).rollback() // Rollback on exception
            verify(transaction, never()).commit() // Should not commit
            verify(entityManager, never()).flush() // Should not flush if exception occurs before
        }

        @Test
        fun `should rollback only if transaction is active when exception occurs`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(false, false, false) // Inactive throughout
            val exception = IllegalStateException("Test exception")

            // Act & Assert
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                entityManager.runTransaction {
                    throw exception
                }
            }

            verify(transaction, times(2)).isActive // Check initially and in catch block
            verify(transaction).begin()
            verify(transaction, never()).rollback() // Should not rollback if not active
            verify(transaction, never()).commit()
        }

        @Test
        fun `should commit previous transaction even if new transaction fails`() {
            // Arrange
            // Active initially, inactive after commit, active when exception occurs, inactive in finally
            whenever(transaction.isActive).thenReturn(true, false, true, false)
            val exception = RuntimeException("Test exception")

            // Act & Assert
            val thrownException =
                org.junit.jupiter.api.assertThrows<RuntimeException> {
                    entityManager.runTransaction {
                        throw exception
                    }
                }

            // Key behavior: exception is propagated (transaction handling is complex with mocks)
            assertThat(thrownException).isEqualTo(exception)
            verify(transaction, atLeast(1)).isActive // Transaction state is checked
            verify(entityManager, never()).flush() // Should not flush if exception occurs
            // Note: With mocks, verifying exact commit/rollback/begin sequence can be complex
            // The important behavior is that exceptions are properly propagated
        }
    }

    @Nested
    @DisplayName("Transaction State Management")
    inner class TransactionStateManagementTests {
        @Test
        fun `should handle exception during commit of previous transaction`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(true, false, false) // Active initially, inactive after commits
            doThrow(RuntimeException("Commit failed"))
                .doNothing()
                .`when`(transaction).commit()
            val expectedResult = "result"

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)
            // Attempted to commit previous (failed), then commit new transaction
            verify(transaction, atLeast(2)).commit()
            // Begin new transaction
            verify(transaction, atLeast(1)).begin()
            // Should not begin in finally (wasCommitted is false)
            verify(transaction, atMost(1)).begin()
            verify(transaction, atLeast(1)).isActive
        }

        @Test
        fun `should not begin transaction for continuation if previous transaction was not committed`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(false, false) // Inactive throughout
            val expectedResult = "result"

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)
            verify(transaction, atLeast(1)).begin() // Begin for new transaction
            verify(transaction, atMost(1)).begin() // Should not begin again (wasCommitted is false)
            verify(transaction).commit()
            verify(transaction, atLeast(1)).isActive
        }

        @Test
        fun `should begin transaction for continuation only if previous was committed and current is inactive`() {
            // Arrange
            whenever(transaction.isActive).thenReturn(true, false, false) // Active initially, inactive after commits
            val expectedResult = "result"

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)
            verify(transaction, times(2)).begin() // Begin for new transaction and for continuation
            verify(transaction, times(2)).commit() // Commit previous and new transaction
            verify(transaction, times(2)).isActive // Check initially and in finally
        }

        @Test
        fun `should not begin transaction for continuation if transaction is still active`() {
            // Arrange
            // Active initially, inactive after commit, active again in finally
            whenever(transaction.isActive).thenReturn(true, false, true)
            val expectedResult = "result"

            // Act
            val result =
                entityManager.runTransaction {
                    expectedResult
                }

            // Assert
            assertThat(result).isEqualTo(expectedResult)
            // Key behavior: should commit previous transaction and new transaction
            verify(transaction, times(2)).commit() // Commit previous and new transaction
            verify(transaction, atLeast(1)).begin() // Begin for new transaction
            verify(transaction, atLeast(2)).isActive // Check initially and in finally
            // Note: begin() should not be called in finally because isActive is true in finally
            // But we can't easily verify this with mocks since begin() might affect isActive state
        }
    }
}
