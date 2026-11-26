package ua.labrunner.api

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import ua.labrunner.contracts.SubmissionStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration test against a real PostgreSQL 16 started by Testcontainers
 * (the same image as in docker-compose.yml). Requires Docker, which is
 * available on GitHub-hosted Linux runners.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {
    // One container for the whole class: started before @BeforeAll uses its JDBC URL.
    private val postgres = PostgreSQLContainer("postgres:16-alpine")

    private lateinit var database: Database

    @BeforeAll
    fun setUp() {
        postgres.start()
        database =
            Database(
                AppConfig(
                    port = 0,
                    dbUrl = postgres.jdbcUrl,
                    dbUser = postgres.username,
                    dbPassword = postgres.password,
                    dbPoolSize = 2,
                    redisHost = "unused",
                    redisPort = 0,
                    redisPassword = "unused",
                    s3Endpoint = "http://unused",
                    s3AccessKey = "unused",
                    s3SecretKey = "unused",
                    s3Bucket = "unused",
                    version = "test",
                ),
            )
        database.migrate()
        database.seedIfEmpty()
    }

    @AfterAll
    fun tearDown() {
        database.close()
        postgres.stop()
    }

    @Test
    fun `migration is idempotent and seed runs only once`() {
        database.migrate()
        database.seedIfEmpty()
        assertEquals(3, database.listAssignments().size)
    }

    @Test
    fun `stored submission is returned with assignment data and QUEUED status`() {
        val assignment = database.listAssignments().first()
        val created = database.createSubmission(assignment.id, "Тест Студент", null, "fun main() {}")

        assertEquals(SubmissionStatus.QUEUED, created.status)
        assertEquals(assignment.title, created.assignmentTitle)
        assertEquals(assignment.maxScore, created.maxScore)
        assertNull(created.score)

        val found = assertNotNull(database.findSubmission(UUID.fromString(created.id)))
        assertEquals(created.id, found.id)
        assertEquals(listOf(created.id), database.listSubmissions("Тест Студент").map { it.id })
    }

    @Test
    fun `unknown submission and assignment return null`() {
        assertNull(database.findSubmission(UUID.randomUUID()))
        assertNull(database.findAssignment(9_999))
    }
}
