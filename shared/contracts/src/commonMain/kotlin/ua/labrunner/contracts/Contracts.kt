package ua.labrunner.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Programming language / runtime image used to check a submission. */
@Serializable
enum class Language {
    @SerialName("kotlin") KOTLIN,
    @SerialName("python") PYTHON,
    @SerialName("cpp") CPP,
}

@Serializable
enum class SubmissionStatus {
    QUEUED,
    RUNNING,
    PASSED,
    FAILED,
    ERROR,
    ;

    val isFinal: Boolean get() = this == PASSED || this == FAILED || this == ERROR
}

@Serializable
data class AssignmentDto(
    val id: Long,
    val title: String,
    val description: String,
    val language: Language,
    val maxScore: Int,
    val deadline: String? = null,
)

@Serializable
data class CreateAssignmentRequest(
    val title: String,
    val description: String,
    val language: Language,
    val maxScore: Int = 10,
    val deadline: String? = null,
)

@Serializable
data class CreateSubmissionRequest(
    val assignmentId: Long,
    val studentName: String,
    val repoUrl: String? = null,
    val sourceCode: String? = null,
)

@Serializable
data class SubmissionDto(
    val id: String,
    val assignmentId: Long,
    val assignmentTitle: String,
    val studentName: String,
    val status: SubmissionStatus,
    val score: Int? = null,
    val maxScore: Int,
    val summary: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** One check inside a test report produced by the Runner Orchestrator. */
@Serializable
data class CheckResult(
    val name: String,
    val passed: Boolean,
    val details: String,
)

@Serializable
data class TestReport(
    val submissionId: String,
    val runner: String,
    val checks: List<CheckResult>,
    val score: Int,
    val maxScore: Int,
    val durationMs: Long,
)

@Serializable
data class HealthDto(
    val status: String,
    val service: String,
    val version: String,
)

@Serializable
data class ErrorDto(val error: String)

/** Names shared by the API (producer) and the orchestrator (consumer). */
object QueueNames {
    const val SUBMISSIONS_STREAM = "labrunner:submissions"
    const val ORCHESTRATOR_GROUP = "orchestrators"
    const val FIELD_SUBMISSION_ID = "submissionId"
}
