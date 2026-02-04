package io.github.salomax.neotool.common.test.transaction

import jakarta.persistence.EntityManager
import org.hibernate.Session

/**
 * Transaction scope functions for test utilities.
 *
 * These utilities help manage transactions in tests, particularly useful when
 * you need to commit data before making HTTP requests that run in separate transactions.
 *
 * Executes a block in a new transaction that commits independently.
 *
 * @param block The code block to execute in the new transaction
 * @return The result of the block
 * @throws Exception if the block throws, the transaction is rolled back
 *
 * @example
 * ```kotlin
 * entityManager.runTransaction {
 *     userRepository.save(user)
 *     entityManager.flush()
 * }
 * ```
 */
inline fun <T> EntityManager.runTransaction(block: () -> T): T {
    val session = unwrap(Session::class.java)
    val transaction = session.transaction

    val wasActive = transaction.isActive
    val wasCommitted =
        try {
            if (wasActive) {
                transaction.commit()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

    try {
        // Begin new transaction
        transaction.begin()
        val result = block()
        flush()
        transaction.commit()
        return result
    } catch (e: Exception) {
        if (transaction.isActive) {
            transaction.rollback()
        }
        throw e
    } finally {
        // If we committed a previous transaction, start a new one for test continuation
        if (wasCommitted && !transaction.isActive) {
            transaction.begin()
        }
    }
}
