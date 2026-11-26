package ua.labrunner.api

import ua.labrunner.contracts.CreateSubmissionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubmissionValidatorTest {
    private fun request(
        name: String = "Владислав Петрик",
        repo: String? = null,
        code: String? = "fun main() {}",
        assignmentId: Long = 1,
    ) = CreateSubmissionRequest(assignmentId, name, repo, code)

    @Test
    fun `trims student name and repository url`() {
        val valid = SubmissionValidator.validate(request(name = "  Влад  ", repo = "  https://github.com/a/b ", code = null))
        assertEquals("Влад", valid.studentName)
        assertEquals("https://github.com/a/b", valid.repoUrl)
        assertNull(valid.sourceCode)
    }

    @Test
    fun `blank code and empty url count as missing payload`() {
        val e = assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(repo = "  ", code = "   ")) }
        assertEquals("repoUrl or sourceCode is required", e.message)
    }

    @Test
    fun `rejects blank student name`() {
        assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(name = "   ")) }
    }

    @Test
    fun `rejects plain http repository url`() {
        assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(repo = "http://github.com/a/b", code = null)) }
    }

    @Test
    fun `rejects source code larger than 64 KiB`() {
        val big = "x".repeat(SubmissionValidator.MAX_SOURCE_BYTES + 1)
        assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(code = big)) }
    }

    @Test
    fun `size limit counts UTF-8 bytes, not characters`() {
        // Cyrillic letters take 2 bytes each in UTF-8.
        val cyrillic = "ї".repeat(SubmissionValidator.MAX_SOURCE_BYTES / 2 + 1)
        assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(code = cyrillic)) }
    }

    @Test
    fun `rejects non-positive assignment id`() {
        assertFailsWith<IllegalArgumentException> { SubmissionValidator.validate(request(assignmentId = 0)) }
    }
}
