package ua.labrunner.app

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import ua.labrunner.contracts.CreateSubmissionRequest
import ua.labrunner.contracts.Language
import ua.labrunner.contracts.SubmissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LabRunnerApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun parsesAssignmentsFromApi() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/assignments", request.url.encodedPath)
                    respond(
                        """[{"id":1,"title":"Лаб 1. FizzBuzz","description":"d","language":"kotlin","maxScore":10}]""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            val list = LabRunnerApi("http://test", engine).assignments()
            assertEquals(1, list.size)
            assertEquals(Language.KOTLIN, list.single().language)
        }

    @Test
    fun submitPostsJsonBodyAndReadsQueuedSubmission() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    val body = (request.body as TextContent).text
                    assertTrue(body.contains("\"studentName\":\"Влад\""), body)
                    respond(
                        """{"id":"7f1c","assignmentId":1,"assignmentTitle":"Лаб 1","studentName":"Влад","status":"QUEUED",
                   "maxScore":10,"createdAt":"2026-11-26T20:30:47Z","updatedAt":"2026-11-26T20:30:47Z"}""",
                        HttpStatusCode.Accepted,
                        jsonHeaders,
                    )
                }
            val created =
                LabRunnerApi("http://test/", engine)
                    .submit(CreateSubmissionRequest(assignmentId = 1, studentName = "Влад", sourceCode = "fun main() {}"))
            assertEquals(SubmissionStatus.QUEUED, created.status)
        }

    @Test
    fun serverErrorMessageIsExposedToTheUi() =
        runTest {
            val engine =
                MockEngine {
                    respond("""{"error":"repoUrl or sourceCode is required"}""", HttpStatusCode.BadRequest, jsonHeaders)
                }
            val e =
                assertFailsWith<ApiException> {
                    LabRunnerApi("http://test", engine).submit(CreateSubmissionRequest(assignmentId = 1, studentName = "Влад"))
                }
            assertEquals("repoUrl or sourceCode is required", e.message)
        }
}
