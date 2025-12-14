package io.github.salomax.neotool.security.test

import com.password4j.Password
import org.junit.jupiter.api.Test

/**
 * Temporary test to generate password hash for test user migration
 * Run this test to get the hash for the test user password "test"
 *
 * To generate the hash, run:
 * ./gradlew :security:test --tests "io.github.salomax.neotool.security.test.GenerateTestUserHash.generateTestUserHash"
 */
class GenerateTestUserHash {
    @Test
    fun generateTestUserHash() {
        val password = "test"
        val hash =
            Password.hash(password)
                .addRandomSalt()
                .withArgon2()
                .result
        println("=".repeat(80))
        println("Password: $password")
        println("Hash: $hash")
        println("=".repeat(80))
        println("\nUse this hash in the migration file V0_12__add_test_user.sql")
        // Also write to file for easy retrieval
        val outputFile = java.io.File("test-user-hash.txt")
        outputFile.writeText("Password: $password\nHash: $hash\n")
        println("\nHash also written to: ${outputFile.absolutePath}")
    }
}
