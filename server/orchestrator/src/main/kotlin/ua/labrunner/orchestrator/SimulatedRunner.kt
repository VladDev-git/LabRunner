package ua.labrunner.orchestrator

import ua.labrunner.contracts.CheckResult
import ua.labrunner.contracts.Language
import ua.labrunner.contracts.TestReport
import java.net.URI
import kotlin.math.roundToInt

/**
 * Lab 2 stage of the runner.
 *
 * The orchestrator is a trusted service and must never execute student code
 * itself (ADR-0001). Until the sandbox (ephemeral Kubernetes Job in gVisor) is
 * introduced in the Kubernetes lab, this runner performs only static checks of
 * the submission. The report format is already the final one, so the API and the
 * mobile client will not change when the real sandbox replaces this class.
 */
class SimulatedRunner {
    fun check(
        job: SubmissionJob,
        durationMs: Long,
    ): TestReport {
        val checks =
            buildList {
                job.sourceCode?.let { addAll(checkSource(job.language, it)) }
                job.repoUrl?.let { addAll(checkRepoUrl(it)) }
            }
        val passed = checks.count { it.passed }
        val score = if (checks.isEmpty()) 0 else (job.maxScore * passed.toDouble() / checks.size).roundToInt()
        return TestReport(
            submissionId = job.id.toString(),
            runner = RUNNER_ID,
            checks = checks,
            score = score,
            maxScore = job.maxScore,
            durationMs = durationMs,
        )
    }

    internal fun checkSource(
        language: Language,
        code: String,
    ): List<CheckResult> {
        val bytes = code.encodeToByteArray().size
        val entryPoint =
            when (language) {
                Language.KOTLIN -> Regex("""fun\s+main\s*\(""")
                Language.PYTHON -> Regex("""(^|\n)\s*def\s+\w+\s*\(|print\s*\(""")
                Language.CPP -> Regex("""int\s+main\s*\(""")
            }
        val forbidden =
            when (language) {
                Language.KOTLIN -> listOf("ProcessBuilder", "Runtime.getRuntime", "System.exit")
                Language.PYTHON -> listOf("import os", "import subprocess", "import socket", "__import__")
                Language.CPP -> listOf("system(", "fork(", "<sys/socket.h>")
            }.filter { it in code }

        return listOf(
            CheckResult("Код не порожній", code.isNotBlank(), "${code.lines().size} рядків"),
            CheckResult("Розмір до 64 КіБ", bytes <= 64 * 1024, "$bytes байт"),
            CheckResult(
                "Точка входу (${language.name.lowercase()})",
                entryPoint.containsMatchIn(code),
                if (entryPoint.containsMatchIn(code)) "знайдено" else "не знайдено: очікується ${entryPoint.pattern}",
            ),
            CheckResult("Збалансовані дужки", bracketsBalanced(code), "() [] {}"),
            CheckResult(
                "Без заборонених викликів",
                forbidden.isEmpty(),
                if (forbidden.isEmpty()) "не виявлено" else "виявлено: ${forbidden.joinToString()}",
            ),
        )
    }

    internal fun checkRepoUrl(raw: String): List<CheckResult> {
        val uri = runCatching { URI(raw.trim()) }.getOrNull()
        val segments =
            uri
                ?.path
                ?.trim('/')
                ?.split('/')
                ?.filter { it.isNotBlank() }
                .orEmpty()
        return listOf(
            CheckResult("URL у форматі HTTPS", uri?.scheme == "https", raw),
            CheckResult("Дозволений Git-хостинг", uri?.host in ALLOWED_HOSTS, uri?.host ?: "—"),
            CheckResult("Шлях owner/repository", segments.size >= 2, segments.joinToString("/").ifEmpty { "—" }),
        )
    }

    private fun bracketsBalanced(code: String): Boolean {
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        val stack = ArrayDeque<Char>()
        for (ch in code) {
            when (ch) {
                '(', '[', '{' -> stack.addLast(ch)
                ')', ']', '}' -> if (stack.removeLastOrNull() != pairs[ch]) return false
            }
        }
        return stack.isEmpty()
    }

    companion object {
        const val RUNNER_ID = "simulated-static-v1"
        private val ALLOWED_HOSTS = setOf("github.com", "gitlab.com")
    }
}
