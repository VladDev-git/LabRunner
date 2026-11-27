package ua.labrunner.api

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import ua.labrunner.contracts.AssignmentDto
import ua.labrunner.contracts.CreateAssignmentRequest
import ua.labrunner.contracts.Language
import ua.labrunner.contracts.SubmissionDto
import ua.labrunner.contracts.SubmissionStatus
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

class Database(
    config: AppConfig,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(Database::class.java)

    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = config.dbPoolSize
                poolName = "api-pool"
            },
        )

    fun <T> tx(block: (Connection) -> T): T =
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                block(c).also { c.commit() }
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }

    fun isHealthy(): Boolean =
        runCatching {
            dataSource.connection.use { it.isValid(2) }
        }.getOrDefault(false)

    /**
     * Applies SQL migrations from resources/db/migration in order.
     * A Postgres advisory lock prevents two API replicas from migrating at once.
     */
    fun migrate() =
        tx { c ->
            c.createStatement().use { st ->
                st.execute("SELECT pg_advisory_xact_lock(424242)")
                st.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (version INT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())",
                )
            }
            val applied =
                c.createStatement().use { st ->
                    st.executeQuery("SELECT version FROM schema_migrations").use { rs ->
                        buildSet { while (rs.next()) add(rs.getInt(1)) }
                    }
                }
            for ((version, file) in MIGRATIONS) {
                if (version in applied) continue
                val sql =
                    javaClass.getResource("/db/migration/$file")?.readText()
                        ?: error("Migration $file not found")
                c.createStatement().use { it.execute(sql) }
                c.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)").use {
                    it.setInt(1, version)
                    it.executeUpdate()
                }
                log.info("Applied migration {}", file)
            }
        }

    fun seedIfEmpty() =
        tx { c ->
            val count =
                c.createStatement().use { st ->
                    st.executeQuery("SELECT count(*) FROM assignments").use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                }
            if (count > 0) return@tx
            SEED.forEach { insertAssignment(c, it) }
            log.info("Seeded {} demo assignments", SEED.size)
        }

    fun listAssignments(): List<AssignmentDto> =
        tx { c ->
            c.prepareStatement("SELECT * FROM assignments ORDER BY id").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toAssignment()) } }
            }
        }

    fun findAssignment(id: Long): AssignmentDto? =
        tx { c ->
            c.prepareStatement("SELECT * FROM assignments WHERE id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toAssignment() else null }
            }
        }

    fun createAssignment(req: CreateAssignmentRequest): AssignmentDto = tx { c -> insertAssignment(c, req) }

    private fun insertAssignment(
        c: Connection,
        req: CreateAssignmentRequest,
    ): AssignmentDto =
        c
            .prepareStatement(
                "INSERT INTO assignments(title, description, language, max_score, deadline) VALUES (?, ?, ?, ?, ?::timestamptz) RETURNING *",
            ).use { ps ->
                ps.setString(1, req.title)
                ps.setString(2, req.description)
                ps.setString(3, req.language.name.lowercase())
                ps.setInt(4, req.maxScore)
                ps.setString(5, req.deadline)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.toAssignment()
                }
            }

    fun createSubmission(
        assignmentId: Long,
        studentName: String,
        repoUrl: String?,
        sourceCode: String?,
    ): SubmissionDto =
        tx { c ->
            val id = UUID.randomUUID()
            c
                .prepareStatement(
                    "INSERT INTO submissions(id, assignment_id, student_name, repo_url, source_code, status) VALUES (?, ?, ?, ?, ?, 'QUEUED')",
                ).use { ps ->
                    ps.setObject(1, id)
                    ps.setLong(2, assignmentId)
                    ps.setString(3, studentName)
                    ps.setString(4, repoUrl)
                    ps.setString(5, sourceCode)
                    ps.executeUpdate()
                }
            selectSubmissions(c, "s.id = ?", listOf(id)).single()
        }

    fun findSubmission(id: UUID): SubmissionDto? = tx { c -> selectSubmissions(c, "s.id = ?", listOf(id)).firstOrNull() }

    /** Number of submissions in the given status; used by the backlog gauge (metrics). */
    fun countByStatus(status: String): Int =
        runCatching {
            tx { c ->
                c.prepareStatement("SELECT count(*) FROM submissions WHERE status = ?").use { ps ->
                    ps.setString(1, status)
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                }
            }
        }.getOrDefault(-1)

    fun reportKey(id: UUID): String? =
        tx { c ->
            c.prepareStatement("SELECT report_key FROM submissions WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    fun listSubmissions(student: String?): List<SubmissionDto> =
        tx { c ->
            if (student.isNullOrBlank()) {
                selectSubmissions(c, "TRUE", emptyList())
            } else {
                selectSubmissions(c, "s.student_name = ?", listOf(student))
            }
        }

    private fun selectSubmissions(
        c: Connection,
        where: String,
        params: List<Any>,
    ): List<SubmissionDto> =
        c
            .prepareStatement(
                """
                SELECT s.*, a.title AS assignment_title, a.max_score
                FROM submissions s JOIN assignments a ON a.id = s.assignment_id
                WHERE $where
                ORDER BY s.created_at DESC
                LIMIT 50
                """.trimIndent(),
            ).use { ps ->
                params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toSubmission()) } }
            }

    override fun close() {
        log.info("Closing database pool")
        dataSource.close()
    }

    private fun ResultSet.toAssignment() =
        AssignmentDto(
            id = getLong("id"),
            title = getString("title"),
            description = getString("description"),
            language = Language.valueOf(getString("language").uppercase()),
            maxScore = getInt("max_score"),
            deadline = getObject("deadline", OffsetDateTime::class.java)?.toString(),
        )

    private fun ResultSet.toSubmission() =
        SubmissionDto(
            id = getObject("id", UUID::class.java).toString(),
            assignmentId = getLong("assignment_id"),
            assignmentTitle = getString("assignment_title"),
            studentName = getString("student_name"),
            status = SubmissionStatus.valueOf(getString("status")),
            score = getObject("score") as Int?,
            maxScore = getInt("max_score"),
            summary = getString("summary"),
            createdAt = getObject("created_at", OffsetDateTime::class.java).toString(),
            updatedAt = getObject("updated_at", OffsetDateTime::class.java).toString(),
        )

    private companion object {
        val MIGRATIONS = listOf(1 to "V1__init.sql")

        val SEED =
            listOf(
                CreateAssignmentRequest(
                    title = "Лаб 1. FizzBuzz",
                    description = "Виведіть числа від 1 до 100, замінюючи кратні 3 на Fizz, кратні 5 на Buzz, а кратні 15 на FizzBuzz.",
                    language = Language.KOTLIN,
                    maxScore = 10,
                    deadline = "2026-12-05T23:59:00+02:00",
                ),
                CreateAssignmentRequest(
                    title = "Лаб 2. Сортування злиттям",
                    description = "Реалізуйте функцію merge_sort(items) та поверніть новий відсортований список.",
                    language = Language.PYTHON,
                    maxScore = 10,
                    deadline = "2026-12-12T23:59:00+02:00",
                ),
                CreateAssignmentRequest(
                    title = "Лаб 3. Стек на масиві",
                    description = "Реалізуйте стек з операціями push, pop, top та empty без використання STL-контейнерів.",
                    language = Language.CPP,
                    maxScore = 15,
                    deadline = "2026-12-19T23:59:00+02:00",
                ),
            )
    }
}
