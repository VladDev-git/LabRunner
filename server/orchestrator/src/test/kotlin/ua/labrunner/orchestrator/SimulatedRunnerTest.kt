package ua.labrunner.orchestrator

import ua.labrunner.contracts.Language
import ua.labrunner.contracts.SubmissionStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatedRunnerTest {
    private val runner = SimulatedRunner()

    private fun job(
        language: Language,
        code: String? = null,
        repo: String? = null,
    ) = SubmissionJob(UUID.randomUUID(), SubmissionStatus.QUEUED, language, maxScore = 10, repoUrl = repo, sourceCode = code)

    @Test
    fun `valid kotlin program gets full score`() {
        val report = runner.check(job(Language.KOTLIN, code = "fun main() {\n    println(\"Hello\")\n}"), durationMs = 1)
        assertEquals(10, report.score)
        assertTrue(report.checks.all { it.passed })
    }

    @Test
    fun `missing entry point and forbidden call lower the score`() {
        val report = runner.check(job(Language.PYTHON, code = "import os\nx = [1, 2]"), durationMs = 1)
        assertEquals(6, report.score)
        assertFalse(report.checks.first { it.name.startsWith("Без заборонених") }.passed)
    }

    @Test
    fun `repository url must point to an allowed host`() {
        val checks = runner.checkRepoUrl("http://example.com/repo")
        assertEquals(listOf(false, false, false), checks.map { it.passed })
        assertTrue(runner.checkRepoUrl("https://github.com/VladDev-git/LabRunner").all { it.passed })
    }
}
