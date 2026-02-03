package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import jakarta.inject.Singleton

/**
 * Factory for creating channel-specific template renderers.
 *
 * Uses Strategy pattern to dispatch to the correct renderer based on channel.
 */
@Singleton
class TemplateRendererFactory(
    private val emailRenderer: EmailRenderer,
    private val pushRenderer: PushRenderer,
    private val whatsAppRenderer: WhatsAppRenderer,
    private val inAppRenderer: InAppRenderer,
    private val chatRenderer: ChatRenderer,
) {
    /**
     * Get renderer for a specific channel.
     *
     * @param channel Communication channel
     * @return Template renderer for the channel
     * @throws IllegalArgumentException if channel is not supported
     */
    fun getRenderer(channel: Channel): TemplateRenderer {
        return when (channel) {
            Channel.EMAIL -> emailRenderer
            Channel.PUSH -> pushRenderer
            Channel.WHATSAPP -> whatsAppRenderer
            Channel.IN_APP -> inAppRenderer
            Channel.CHAT -> chatRenderer
        }
    }
}
