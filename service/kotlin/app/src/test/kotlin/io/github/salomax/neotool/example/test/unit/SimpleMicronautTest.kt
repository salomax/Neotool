package io.github.salomax.neotool.example.test.unit

import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@MicronautTest
@DisplayName("Simple Micronaut Test")
@Tag("integration")
class SimpleMicronautTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `health endpoint should be accessible`() {
        httpClient.exchangeAsString(
            HttpRequest.GET<Any>("/health"),
        ).shouldBeSuccessful()
    }
}
