package ua.labrunner.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import ua.labrunner.contracts.CreateAssignmentRequest
import ua.labrunner.contracts.CreateSubmissionRequest
import ua.labrunner.contracts.ErrorDto
import ua.labrunner.contracts.HealthDto
import java.util.UUID

private val log = LoggerFactory.getLogger("ua.labrunner.api.Application")

fun main() {
    val config = AppConfig.fromEnv()

    // Backing services (factor IV) are attached through configuration only.
    val database = Database(config)
    database.migrate()
    database.seedIfEmpty()
    val queue = SubmissionQueue(config)
    val storage = ReportStorage(config)
    storage.ensureBucket()

    val server =
        embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            module(config, database, queue, storage)
        }

    // Factor IX (disposability): on SIGTERM / SIGINT finish in-flight requests,
    // then release the DB pool and the Redis connection (see ApplicationStopped).
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutdown signal received, stopping HTTP server gracefully")
            server.stop(gracePeriodMillis = 3_000, timeoutMillis = 10_000)
        },
    )

    log.info("LabRunner API {} listening on port {}", config.version, config.port)
    server.start(wait = true)
}

fun Application.module(
    config: AppConfig,
    database: Database,
    queue: SubmissionQueue,
    storage: ReportStorage,
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path() != "/health" }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Bad request"))
        }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Internal server error"))
        }
    }

    monitor.subscribe(ApplicationStopped) {
        queue.close()
        database.close()
        log.info("API stopped")
    }

    routing {
        get("/health") {
            val healthy = database.isHealthy() && queue.isHealthy()
            call.respond(
                if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthDto(if (healthy) "UP" else "DOWN", "api", config.version),
            )
        }

        route("/api/v1") {
            get("/assignments") { call.respond(database.listAssignments()) }

            post("/assignments") {
                val req = call.receive<CreateAssignmentRequest>()
                require(req.title.isNotBlank()) { "title must not be blank" }
                require(req.maxScore in 1..100) { "maxScore must be in 1..100" }
                call.respond(HttpStatusCode.Created, database.createAssignment(req))
            }

            post("/submissions") {
                val valid = SubmissionValidator.validate(call.receive<CreateSubmissionRequest>())
                requireNotNull(database.findAssignment(valid.assignmentId)) { "assignment ${valid.assignmentId} not found" }

                // The submission is stored first and only then queued, so a crash
                // between the two steps never loses an accepted submission.
                val submission = database.createSubmission(valid.assignmentId, valid.studentName, valid.repoUrl, valid.sourceCode)
                val messageId = queue.publish(submission.id)
                log.info("Submission {} queued as stream message {}", submission.id, messageId)
                call.respond(HttpStatusCode.Accepted, submission)
            }

            get("/submissions") {
                call.respond(database.listSubmissions(call.request.queryParameters["student"]))
            }

            get("/submissions/{id}") {
                val id = parseId(call.parameters["id"])
                val submission = database.findSubmission(id)
                if (submission == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorDto("submission not found"))
                } else {
                    call.respond(submission)
                }
            }

            get("/submissions/{id}/report") {
                val id = parseId(call.parameters["id"])
                val report = database.reportKey(id)?.let(storage::read)
                if (report == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorDto("report is not ready"))
                } else {
                    call.respondText(report, ContentType.Application.Json)
                }
            }
        }
    }
}

private fun parseId(raw: String?): UUID =
    runCatching { UUID.fromString(raw) }.getOrElse { throw IllegalArgumentException("invalid submission id") }
