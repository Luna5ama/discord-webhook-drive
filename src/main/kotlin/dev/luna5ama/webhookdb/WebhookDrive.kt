package dev.luna5ama.webhookdb

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.toByteArray

class WebhookDrive(private val access: IWebhookAccess, private val rootMessageID: Long) : AutoCloseable {
    private var paths = ConcurrentHashMap<String, LongArray>()

    private val bufferLimiter = Semaphore(16)

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun init() {
        paths = ConcurrentHashMap(getTree())
    }

    override fun close() {
        runBlocking {
            closeSuspend()
        }
    }

    suspend fun closeSuspend() {
        putTree(paths)
    }

    private suspend fun getTree(): Map<String, LongArray> {
        val message = access.getWebhookMessage(rootMessageID)
        val url = message.attachments.first().url
        val response = httpClient.get(url)
        return runCatching {
            response.body<Map<String, LongArray>>()
        }.getOrNull() ?: emptyMap()
    }

    private suspend fun putTree(map: Map<String, LongArray>) {
        val filePart = FormPart(
            "file[0]",
            json.encodeToString(map).toByteArray(),
            headersOf(HttpHeaders.ContentDisposition, "filename=tree.json")
        )
        runCatching {
            access.getWebhookMessage(rootMessageID)
        }.onFailure {
            access.executeWebhook(filePart)
        }.onSuccess {
            access.editWebhookMessage(
                rootMessageID,
                OutgoingWebhook(attachments = emptyList()),
                filePart
            )
        }
    }

    suspend fun put(path: String, data: ByteReadChannel) {
        val removed = paths.remove(path)
        coroutineScope {
            removed?.forEach {
                launch {
                    access.deleteWebhookMessage(it)
                }
            }

            val pendingID = mutableListOf<Deferred<Long>>()

            var packet: ByteReadPacket
            while (data.readRemaining(24 * 1024 * 1024).also { packet = it }.isNotEmpty) {
                bufferLimiter.acquire()
                val i = pendingID.size
                val p = packet

                pendingID.add(async {
                    val filePart = FormPart(
                        "file[0]",
                        p,
                        headersOf(HttpHeaders.ContentDisposition, "filename=$i")
                    )

                    val result = access.executeWebhook(OutgoingWebhook(content = path), filePart).id
                    bufferLimiter.release()
                    result
                })
            }

            paths[path] = pendingID.awaitAll().toLongArray()
        }
    }

    @OptIn(FlowPreview::class)
    suspend fun get(path: String): ByteReadChannel? {
        val ids = paths[path] ?: return null

        val output = ByteChannel()

        CoroutineScope(Dispatchers.IO).launch {
            ids.asFlow().map {
                bufferLimiter.acquire()
                async {
                    val url = access.getWebhookMessage(it).attachments.first().url
                    val response = httpClient.get(url)
                    response.body<ByteArray>()
                }
            }.produceIn(this).consumeEach {
                output.writeFully(it.await())
                bufferLimiter.release()
            }

            output.close()
        }

        return output
    }

    suspend fun delete(path: String) {
        val ids = paths.remove(path) ?: return
        coroutineScope {
            ids.forEach {
                launch {
                    access.deleteWebhookMessage(it)
                }
            }
        }
    }
}