package dev.luna5ama.webhookdb

import io.ktor.client.request.forms.*

interface IWebhookAccess {
    suspend fun executeWebhook(json: OutgoingWebhook): Message

    suspend fun executeWebhook(vararg fileParts: FormPart<*>): Message =
        executeWebhook(null, fileParts.toList())

    suspend fun executeWebhook(fileParts: List<FormPart<*>>): Message =
        executeWebhook(null, fileParts)

    suspend fun executeWebhook(json: OutgoingWebhook?, vararg fileParts: FormPart<*>): Message =
        executeWebhook(json, fileParts.toList())

    suspend fun executeWebhook(json: OutgoingWebhook?, fileParts: List<FormPart<*>>): Message

    suspend fun getWebhookMessage(id: Long): Message

    suspend fun editWebhookMessage(id: Long, json: OutgoingWebhook): Message

    suspend fun editWebhookMessage(id: Long, vararg fileParts: FormPart<*>): Message =
        editWebhookMessage(id, null, fileParts.toList())

    suspend fun editWebhookMessage(id: Long, fileParts: List<FormPart<*>>): Message =
        editWebhookMessage(id, null, fileParts)

    suspend fun editWebhookMessage(id: Long, json: OutgoingWebhook?, vararg fileParts: FormPart<*>): Message =
        editWebhookMessage(id, json, fileParts.toList())

    suspend fun editWebhookMessage(id: Long, json: OutgoingWebhook?, fileParts: List<FormPart<*>>): Message

    suspend fun deleteWebhookMessage(id: Long)
}