package io.github.salomax.neotool.banking.infra.assets

import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("AssetsServiceFileStorage IT")
class AssetsServiceFileStorageIT {
    companion object {
        private const val MOCK_SERVER_PORT = 18092

        @JvmStatic
        val mockServer = MockWebServer()

        init {
            mockServer.start(MOCK_SERVER_PORT)
            Runtime.getRuntime().addShutdownHook(Thread { mockServer.shutdown() })
        }

        @JvmStatic
        private fun baseUrl() = "http://localhost:$MOCK_SERVER_PORT"

        @JvmStatic
        private fun graphqlResponse(body: String) =
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body)
    }

    @Inject
    lateinit var importFileStorage: ImportFileStoragePort

    private val accountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()

    @Nested
    @DisplayName("store")
    inner class StoreTests {
        @Test
        fun `store returns assetId when assets GraphQL and presigned PUT are stubbed`() {
            val uploadPath = "/upload-$assetId"
            val uploadUrl = "${AssetsServiceFileStorageIT.baseUrl()}$uploadPath"
            mockServer.enqueue(
                graphqlResponse("""{"data":{"createAssetUpload":{"id":"$assetId","uploadUrl":"$uploadUrl"}}}"""),
            )
            mockServer.enqueue(MockResponse().setResponseCode(200))
            mockServer.enqueue(
                graphqlResponse("""{"data":{"confirmAssetUpload":{"id":"$assetId","status":"READY"}}}"""),
            )

            val bytes = "OFX content".toByteArray(StandardCharsets.UTF_8)
            val result = importFileStorage.store(accountId, bytes, "export.ofx", "application/x-ofx")

            assertThat(result).isEqualTo(assetId)
        }
    }

    @Nested
    @DisplayName("read")
    inner class ReadTests {
        @Test
        fun `read returns bytes from presigned GET when asset query is stubbed`() {
            val downloadPath = "/download-$assetId"
            val downloadUrl = "${AssetsServiceFileStorageIT.baseUrl()}$downloadPath"
            val content = "file bytes"
            mockServer.enqueue(
                graphqlResponse("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
            )
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(content),
            )

            val result = importFileStorage.read(assetId)

            assertThat(String(result, StandardCharsets.UTF_8)).isEqualTo(content)
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {
        @Test
        fun `delete on non-existent asset does not throw`() {
            val nonExistent = UUID.randomUUID()
            mockServer.enqueue(
                graphqlResponse("""{"data":{"deleteAsset":true}}"""),
            )

            importFileStorage.delete(nonExistent)
        }
    }
}
