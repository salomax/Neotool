package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
import io.github.salomax.neotool.assistant.entity.PromptEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.assistant.repository.PromptRepository
import io.github.salomax.neotool.assistant.repository.PromptVersionRepository
import io.github.salomax.neotool.assistant.service.PromptService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class PromptServiceTest {
    private lateinit var promptRepository: PromptRepository
    private lateinit var promptVersionRepository: PromptVersionRepository
    private lateinit var promptService: PromptService

    @BeforeEach
    fun setUp() {
        promptRepository = mock()
        promptVersionRepository = mock()
        promptService = PromptService(promptRepository, promptVersionRepository)
    }

    @Test
    fun `publishPromptVersion sets previous ACTIVE version to DEPRECATED`() {
        val promptId = UUID.randomUUID()
        val targetVersionId = UUID.randomUUID()
        val previousActiveId = UUID.randomUUID()

        val prompt =
            PromptEntity(
                id = promptId,
                name = "Prompt",
                category = "faq",
                description = null,
                tags = emptyArray(),
                createdBy = UUID.randomUUID(),
            )

        val previousActiveVersion =
            PromptVersionEntity(
                id = previousActiveId,
                promptId = promptId,
                version = 1,
                status = PromptVersionStatus.ACTIVE,
                template = "active-v1",
            )

        val targetVersion =
            PromptVersionEntity(
                id = targetVersionId,
                promptId = promptId,
                version = 2,
                status = PromptVersionStatus.DRAFT,
                template = "draft-v2",
            )

        whenever(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt))
        whenever(promptVersionRepository.findById(targetVersionId)).thenReturn(Optional.of(targetVersion))
        whenever(
            promptVersionRepository.findByPromptIdAndStatus(
                promptId,
                PromptVersionStatus.ACTIVE,
            ),
        ).thenReturn(previousActiveVersion)
        whenever(promptVersionRepository.update(previousActiveVersion)).thenReturn(previousActiveVersion)
        whenever(promptVersionRepository.update(targetVersion)).thenReturn(targetVersion)

        val published = promptService.publishPromptVersion(promptId, targetVersionId)

        assertThat(previousActiveVersion.status).isEqualTo(PromptVersionStatus.DEPRECATED)
        assertThat(published.status).isEqualTo(PromptVersionStatus.ACTIVE)
        verify(promptVersionRepository).update(previousActiveVersion)
        verify(promptVersionRepository).update(targetVersion)
    }

    @Test
    fun `renderTemplate replaces all declared variables correctly`() {
        val versionId = UUID.randomUUID()
        val version =
            PromptVersionEntity(
                id = versionId,
                promptId = UUID.randomUUID(),
                version = 1,
                status = PromptVersionStatus.DRAFT,
                template = "Hello {{customerName}}, your plan is \${plan} and locale {{locale}}.",
                variablesSchema =
                    mapOf(
                        "required" to listOf("customerName", "plan"),
                        "properties" to
                            mapOf(
                                "customerName" to mapOf("type" to "string"),
                                "plan" to mapOf("type" to "string"),
                                "locale" to mapOf("type" to "string", "default" to "pt-BR"),
                            ),
                    ),
            )

        whenever(promptVersionRepository.findById(versionId)).thenReturn(Optional.of(version))

        val rendered =
            promptService.renderTemplate(
                versionId = versionId,
                variables =
                    mapOf(
                        "customerName" to "Ana",
                        "plan" to "Pro",
                    ),
            )

        assertThat(rendered).isEqualTo("Hello Ana, your plan is Pro and locale pt-BR.")
    }

    @Test
    fun `renderTemplate raises error for missing required variables`() {
        val versionId = UUID.randomUUID()
        val version =
            PromptVersionEntity(
                id = versionId,
                promptId = UUID.randomUUID(),
                version = 1,
                status = PromptVersionStatus.DRAFT,
                template = "Hello {{customerName}}",
                variablesSchema =
                    mapOf(
                        "required" to listOf("customerName", "plan"),
                        "properties" to
                            mapOf(
                                "customerName" to mapOf("type" to "string"),
                                "plan" to mapOf("type" to "string"),
                            ),
                    ),
            )

        whenever(promptVersionRepository.findById(versionId)).thenReturn(Optional.of(version))

        assertThatThrownBy {
            promptService.renderTemplate(
                versionId = versionId,
                variables = mapOf("customerName" to "Ana"),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Missing required template variables")
            .hasMessageContaining("plan")
    }
}
