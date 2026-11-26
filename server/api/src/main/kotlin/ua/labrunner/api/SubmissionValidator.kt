package ua.labrunner.api

import ua.labrunner.contracts.CreateSubmissionRequest

/** A submission request after trimming and validation. */
data class ValidSubmission(
    val assignmentId: Long,
    val studentName: String,
    val repoUrl: String?,
    val sourceCode: String?,
)

/**
 * Pure validation of an incoming submission, kept separate from Ktor and JDBC
 * so it can be unit-tested without a database or an HTTP server.
 */
object SubmissionValidator {
    const val MAX_SOURCE_BYTES = 64 * 1024
    const val MAX_NAME_LENGTH = 100

    /** Throws [IllegalArgumentException] (mapped to HTTP 400) when the request is invalid. */
    fun validate(req: CreateSubmissionRequest): ValidSubmission {
        val name = req.studentName.trim()
        val repoUrl = req.repoUrl?.trim()?.takeIf { it.isNotEmpty() }
        val code = req.sourceCode?.takeIf { it.isNotBlank() }

        require(req.assignmentId > 0) { "assignmentId must be positive" }
        require(name.isNotEmpty()) { "studentName must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "studentName is longer than $MAX_NAME_LENGTH characters" }
        require(repoUrl != null || code != null) { "repoUrl or sourceCode is required" }
        require(repoUrl == null || repoUrl.startsWith("https://")) { "repoUrl must start with https://" }
        require((code?.encodeToByteArray()?.size ?: 0) <= MAX_SOURCE_BYTES) { "sourceCode is larger than 64 KiB" }

        return ValidSubmission(req.assignmentId, name, repoUrl, code)
    }
}
