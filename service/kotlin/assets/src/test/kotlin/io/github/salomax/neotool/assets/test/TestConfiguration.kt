package io.github.salomax.neotool.assets.test

import io.github.salomax.neotool.assets.storage.StorageClient
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Test configuration that provides mock implementations for testing.
 */
@Factory
class TestConfiguration {
    /**
     * Provides a mock storage client for integration tests.
     * This replaces the real S3StorageClient in test contexts.
     */
    @Bean
    @Singleton
    @Named("mockStorageClient")
    fun mockStorageClient(): MockStorageClient {
        return MockStorageClient()
    }

    /**
     * Provides the mock storage client as the primary StorageClient implementation.
     * This ensures tests use the mock instead of the real implementation.
     */
    @Bean
    @Singleton
    @Primary
    @Replaces(bean = StorageClient::class)
    fun storageClient(@Named("mockStorageClient") mockStorageClient: MockStorageClient): StorageClient {
        return mockStorageClient
    }
}


