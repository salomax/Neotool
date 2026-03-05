package io.github.salomax.neotool.assistant.test.unit.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.agent.AssistantAgent
import io.github.salomax.neotool.assistant.agent.ConversationContext
import io.github.salomax.neotool.assistant.agent.ConversationStatus
import io.github.salomax.neotool.assistant.entity.ConversationEntity
import io.github.salomax.neotool.assistant.entity.ConversationMessageEntity
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.repository.ConversationMessageRepository
import io.github.salomax.neotool.assistant.repository.ConversationRepository
import io.github.salomax.neotool.assistant.service.ConversationService
import io.github.salomax.neotool.assistant.service.EscalationDetector
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class EscalationDetectorTest {
    private lateinit var escalationDetector: EscalationDetector

    @BeforeEach
    fun setUp() {
        escalationDetector = EscalationDetector(ObjectMapper())
    }

    @Test
    fun `detect returns escalation true for valid escalation JSON`() {
        val signal = escalationDetector.detect("""{"escalate":true,"reason":"needs human handoff"}""")

        assertThat(signal.escalate).isTrue()
        assertThat(signal.reason).isEqualTo("needs human handoff")
    }

    @Test
    fun `detect returns escalation false for non JSON response`() {
        val signal = escalationDetector.detect("plain assistant text response")

        assertThat(signal.escalate).isFalse()
        assertThat(signal.reason).isNull()
    }

    @Test
    fun `detect returns escalation false for malformed JSON and never throws`() {
        val signal = escalationDetector.detect("""{"escalate":true,"reason":"missing quote}""")

        assertThat(signal.escalate).isFalse()
        assertThat(signal.reason).isNull()
    }

    @Test
    fun `conversation is marked ESCALATED after escalation detection`() {
        val assistantAgent = mock<AssistantAgent>()
        val assistantMetrics = mock<AssistantMetrics>()
        val conversationRepository = mock<ConversationRepository>()
        val conversationMessageRepository = mock<ConversationMessageRepository>()

        val accountId = UUID.randomUUID()
        val sessionId = "session-escalation-1"
        var storedConversation: ConversationEntity? = null
        var storedMessages: MutableList<ConversationMessageEntity> = mutableListOf()

        whenever(
            conversationRepository.findByAccountIdAndSessionId(accountId, sessionId),
        ).thenAnswer { storedConversation }
        whenever(conversationRepository.findForUpdateByAccountIdAndSessionId(accountId, sessionId)).thenAnswer {
            storedConversation
        }
        whenever(conversationRepository.save(any())).thenAnswer { invocation ->
            val incoming = invocation.arguments[0] as ConversationEntity
            val persisted =
                ConversationEntity(
                    id = incoming.id ?: UUID.randomUUID(),
                    accountId = incoming.accountId,
                    sessionId = incoming.sessionId,
                    status = incoming.status,
                    escalationReason = incoming.escalationReason,
                    createdAt = incoming.createdAt,
                    updatedAt = incoming.updatedAt,
                )
            storedConversation = persisted
            persisted
        }
        whenever(conversationRepository.update(any())).thenAnswer { invocation ->
            val incoming = invocation.arguments[0] as ConversationEntity
            storedConversation = incoming
            incoming
        }

        whenever(conversationMessageRepository.findByConversationIdOrderBySequenceNoAsc(any())).thenAnswer {
            storedMessages.toList()
        }
        whenever(conversationMessageRepository.findMaxSequenceNoByConversationId(any())).thenAnswer {
            storedMessages.maxOfOrNull { row -> row.sequenceNo } ?: 0
        }
        whenever(conversationMessageRepository.deleteByConversationId(any())).thenAnswer {
            storedMessages.clear()
            Unit
        }
        whenever(conversationMessageRepository.saveAll(any<Iterable<ConversationMessageEntity>>())).thenAnswer {
                invocation ->
            val incoming = (invocation.arguments[0] as Iterable<ConversationMessageEntity>).toList()
            storedMessages = incoming.toMutableList()
            incoming
        }

        val service =
            ConversationService(
                assistantAgent = assistantAgent,
                escalationDetector = escalationDetector,
                assistantMetrics = assistantMetrics,
                conversationRepository = conversationRepository,
                conversationMessageRepository = conversationMessageRepository,
            )

        val userMessage = "I need an answer you cannot provide"
        val escalationResponse = """{"escalate":true,"reason":"insufficient confidence"}"""

        runBlocking {
            whenever(assistantAgent.processMessage(eq(userMessage), any())).thenAnswer { invocation ->
                val context = invocation.arguments[1] as ConversationContext
                context.addMessage(MessageRole.USER, userMessage)
                context.addMessage(MessageRole.ASSISTANT, escalationResponse)
                escalationResponse
            }
        }

        val result = runBlocking { service.sendMessage(accountId, sessionId, userMessage) }

        val context = service.getContext(accountId, sessionId)
        assertThat(result.escalate).isTrue()
        assertThat(context).isNotNull
        assertThat(context!!.status).isEqualTo(ConversationStatus.ESCALATED)
        assertThat(context.escalationReason).isEqualTo("insufficient confidence")
        assertThat(result.conversation).isNotNull
        assertThat(result.conversation!!.messages).hasSize(2)
        verify(assistantMetrics).incrementEscalation("insufficient confidence")
    }
}
