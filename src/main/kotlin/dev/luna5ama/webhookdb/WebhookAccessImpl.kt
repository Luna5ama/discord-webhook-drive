package dev.luna5ama.webhookdb

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebhookAccessImpl(private val webhookID: String, private val token: String) : IWebhookAccess {
    constructor(webhookID: Long, token: String) : this(webhookID.toString(), token)

    private var limiter: Job = CompletableDeferred(Unit)

    private val httpClient = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 16
        }
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun executeWebhook(json: OutgoingWebhook): Message {
        val response = tryRequest {
            httpClient.post(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token)
                    parameter("wait", true)
                }
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        }

        return response.body()
    }

    override suspend fun executeWebhook(
        json: OutgoingWebhook?,
        fileParts: List<FormPart<*>>
    ): Message {
        val response = tryRequest {
            httpClient.post(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token)
                    parameter("wait", true)
                }
                contentType(ContentType.MultiPart.FormData)
                setBody(MultiPartFormDataContent(
                    formData {
                        if (json != null) {
                            append("payload_json", Json.encodeToString(json))
                        }
                        fileParts.forEach {
                            append(it)
                        }
                    }
                ))
            }
        }

        return response.body()
    }

    override suspend fun getWebhookMessage(id: Long): Message {
        val response = tryRequest {
            httpClient.get(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token, "messages", id.toString())
                }
            }
        }

        return response.body()
    }

    override suspend fun editWebhookMessage(id: Long, json: OutgoingWebhook): Message {
        val response = tryRequest {
            httpClient.patch(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token, "messages", id.toString())
                }
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        }

        return response.body()
    }

    override suspend fun editWebhookMessage(
        id: Long,
        json: OutgoingWebhook?,
        fileParts: List<FormPart<*>>
    ): Message {
        val response = tryRequest {
            httpClient.patch(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token, "messages", id.toString())
                }
                contentType(ContentType.MultiPart.FormData)
                setBody(MultiPartFormDataContent(
                    formData {
                        if (json != null) {
                            append("payload_json", Json.encodeToString(json))
                        }
                        fileParts.forEach {
                            append(it)
                        }
                    }
                ))
            }
        }

        return response.body()
    }

    override suspend fun deleteWebhookMessage(id: Long) {
        tryRequest {
            httpClient.delete(WEBHOOK_API) {
                url {
                    appendPathSegments(webhookID, token, "messages", id.toString())
                }
            }
        }
    }


    private suspend fun <R> tryRequest(block: suspend () -> R): R {
        while (true) {
            try {
                limiter.join()
                return block()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.TooManyRequests) {
                    runCatching {
                        val retryTime = e.response.body<Map<String, String>>()["retry_after"]!!.toFloat()
                        setRateLimit((retryTime * 1000).toLong())
                    }.onFailure {
                        setRateLimit(500)
                    }

                    continue
                } else {
                    throw e
                }
            } catch (e: HttpRequestTimeoutException) {
                setRateLimit(500)
                continue
            } catch (e : ConnectTimeoutException) {
                setRateLimit(500)
                continue
            }
        }
    }

    private fun setRateLimit(delay: Long) {
        limiter = CoroutineScope(Dispatchers.Default).launch {
            delay(delay)
        }
    }

    companion object {
        private const val WEBHOOK_API = "https://discord.com/api/webhooks"
    }
}