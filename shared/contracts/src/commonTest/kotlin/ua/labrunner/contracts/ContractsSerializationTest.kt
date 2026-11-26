package ua.labrunner.contracts

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The JSON shape is the contract between the Ktor server and the mobile client.
 * These tests run on the JVM and on the iOS simulator (CI), so both sides agree.
 */
class ContractsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun languageUsesLowercaseWireNames() {
        assertEquals("\"kotlin\"", json.encodeToString(Language.serializer(), Language.KOTLIN))
        assertEquals(Language.CPP, json.decodeFromString(Language.serializer(), "\"cpp\""))
    }

    @Test
    fun assignmentDecodesFromServerJson() {
        val raw = """{"id":1,"title":"Лаб 1","description":"d","language":"python","maxScore":10,"extra":true}"""
        val dto = json.decodeFromString(AssignmentDto.serializer(), raw)
        assertEquals(Language.PYTHON, dto.language)
        assertEquals(null, dto.deadline)
    }

    @Test
    fun onlyPassedFailedAndErrorAreFinalStatuses() {
        assertFalse(SubmissionStatus.QUEUED.isFinal)
        assertFalse(SubmissionStatus.RUNNING.isFinal)
        assertTrue(SubmissionStatus.PASSED.isFinal)
        assertTrue(SubmissionStatus.FAILED.isFinal)
        assertTrue(SubmissionStatus.ERROR.isFinal)
    }
}
