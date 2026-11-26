package ua.labrunner.app

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ua.labrunner.contracts.AssignmentDto
import ua.labrunner.contracts.CreateSubmissionRequest
import ua.labrunner.contracts.ErrorDto
import ua.labrunner.contracts.SubmissionDto

/** Thin client for the Core API; DTOs come from :shared:contracts (same classes as on the server). */
class LabRunnerApi(
    baseUrl: String,
    // null = platform engine (OkHttp / Darwin); tests pass a MockEngine.
    engine: HttpClientEngine? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
        defaultRequest { url(baseUrl.trimEnd('/') + "/") }
        expectSuccess = false
    }

    private val client = if (engine != null) HttpClient(engine, config) else HttpClient(config)

    suspend fun assignments(): List<AssignmentDto> = client.get("api/v1/assignments").checked()

    suspend fun submissions(student: String): List<SubmissionDto> =
        client.get("api/v1/submissions") { parameter("student", student) }.checked()

    suspend fun submit(request: CreateSubmissionRequest): SubmissionDto =
        client
            .post("api/v1/submissions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.checked()

    private suspend inline fun <reified T> io.ktor.client.statement.HttpResponse.checked(): T {
        if (status.isSuccess()) return body()
        val message = runCatching { json.decodeFromString<ErrorDto>(bodyAsText()).error }.getOrNull()
        throw ApiException(message ?: "HTTP ${status.value}")
    }
}

class ApiException(
    message: String,
) : Exception(message)
