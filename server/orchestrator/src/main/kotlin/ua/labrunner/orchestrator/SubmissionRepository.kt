package ua.labrunner.orchestrator

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import ua.labrunner.contracts.Language
import ua.labrunner.contracts.SubmissionStatus
import java.util.UUID

data class SubmissionJob(
    val id: UUID,
    val status: SubmissionStatus,
    val language: Language,
    val maxScore: Int,
    val repoUrl: String?,
    val sourceCode: String?,
)

class SubmissionRepository(
    config: OrchestratorConfig,
) : AutoCloseable {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 4
                poolName = "orchestrator-pool"
            },
        )

    fun find(id: UUID): SubmissionJob? =
        dataSource.connection.use { c ->
            c
                .prepareStatement(
                    """
                    SELECT s.id, s.status, s.repo_url, s.source_code, a.language, a.max_score
                    FROM submissions s JOIN assignments a ON a.id = s.assignment_id
                    WHERE s.id = ?
                    """.trimIndent(),
                ).use { ps ->
                    ps.setObject(1, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use null
                        SubmissionJob(
                            id = rs.getObject("id", UUID::class.java),
                            status = SubmissionStatus.valueOf(rs.getString("status")),
                            language = Language.valueOf(rs.getString("language").uppercase()),
                            maxScore = rs.getInt("max_score"),
                            repoUrl = rs.getString("repo_url"),
                            sourceCode = rs.getString("source_code"),
                        )
                    }
                }
        }

    /** Moves QUEUED -> RUNNING. Returns false if the submission was already finished (idempotency). */
    fun markRunning(id: UUID): Boolean =
        update(
            "UPDATE submissions SET status = 'RUNNING', updated_at = now() WHERE id = ? AND status IN ('QUEUED', 'RUNNING')",
        ) { it.setObject(1, id) } == 1

    fun finish(
        id: UUID,
        status: SubmissionStatus,
        score: Int?,
        summary: String,
        reportKey: String?,
    ) {
        update(
            "UPDATE submissions SET status = ?, score = ?, summary = ?, report_key = ?, updated_at = now() WHERE id = ?",
        ) {
            it.setString(1, status.name)
            if (score == null) it.setNull(2, java.sql.Types.INTEGER) else it.setInt(2, score)
            it.setString(3, summary)
            it.setString(4, reportKey)
            it.setObject(5, id)
        }
    }

    fun isHealthy(): Boolean = runCatching { dataSource.connection.use { it.isValid(2) } }.getOrDefault(false)

    private fun update(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit,
    ): Int =
        dataSource.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeUpdate()
            }
        }

    override fun close() = dataSource.close()
}
