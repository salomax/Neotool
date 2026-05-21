package io.github.salomax.neotool.banking.client.assets

import io.micronaut.context.env.Environment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("BillImportAssetStorage")
class BillImportAssetStorageTest {
    private val assetsClient: AssetsServiceClient = mock()
    private val presignedUrlHttpClient: PresignedUrlHttpClient = mock()
    private val environment: Environment = mock()

    @Test
    fun `uses bill import namespace when explicitly configured`() {
        val storage =
            BillImportAssetStorage(
                assetsClient = assetsClient,
                presignedUrlHttpClient = presignedUrlHttpClient,
                billImportNamespace = "bill-imports",
                billDownloadTtlSeconds = 1800,
                environment = environment,
            )

        assertThat(storage.resolveNamespace()).isEqualTo("bill-imports")
        assertThat(storage.resolveDownloadTtlSeconds()).isEqualTo(1800)
    }

    @Test
    fun `falls back to legacy property when bill namespace is unresolved placeholder`() {
        whenever(environment.getProperty("banking.assets-service.pdf-import-namespace", String::class.java))
            .thenReturn(java.util.Optional.of("transaction-imports"))
        whenever(environment.getProperty("banking.assets-service.pdf-download-ttl-seconds", Int::class.java))
            .thenReturn(java.util.Optional.of(7200))

        val storage =
            BillImportAssetStorage(
                assetsClient = assetsClient,
                presignedUrlHttpClient = presignedUrlHttpClient,
                billImportNamespace = "\${banking.assets-service.pdf-import-namespace}",
                billDownloadTtlSeconds = 0,
                environment = environment,
            )

        assertThat(storage.resolveNamespace()).isEqualTo("transaction-imports")
        assertThat(storage.resolveDownloadTtlSeconds()).isEqualTo(7200)
    }
}
