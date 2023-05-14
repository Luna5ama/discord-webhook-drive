package dev.luna5ama.webhookdb

import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val id: Long,
    val filename: String,
    val size: Long,
    val url: String
)

@Serializable
data class Message(
    val id: Long,
    val content: String,
    val attachments: List<Attachment>
)

@Serializable
data class OutgoingWebhook(
    val content: String? = null,
    val attachments: List<PartialAttachment>? = null
) {
    @Serializable
    data class PartialAttachment(
        val filename: String,
        val description: String? = null
    )
}