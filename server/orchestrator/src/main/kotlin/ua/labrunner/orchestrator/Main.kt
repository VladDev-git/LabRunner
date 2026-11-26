package ua.labrunner.orchestrator

import com.sun.net.httpserver.HttpServer
import io.lettuce.core.Consumer
import io.lettuce.core.RedisBusyException
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ua.labrunner.contracts.QueueNames
import ua.labrunner.contracts.SubmissionStatus
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val log = LoggerFactory.getLogger("ua.labrunner.orchestrator.Main")
private val json = Json { prettyPrint = true; encodeDefaults = true }

fun main() {
    val config = OrchestratorConfig.fromEnv()
    val repository = SubmissionRepository(config)
    val runner = SimulatedRunner()

    val s3 = MinioClient.builder()
        .endpoint(config.s3Endpoint)
        .region("us-east-1")
        .credentials(config.s3AccessKey, config.s3SecretKey)
        .build()
    if (!s3.bucketExists(BucketExistsArgs.builder().bucket(config.s3Bucket).build())) {
        s3.makeBucket(MakeBucketArgs.builder().bucket(config.s3Bucket).build())
    }

    val redis = RedisClient.create(
        RedisURI.builder()
            .withHost(config.redisHost)
            .withPort(config.redisPort)
            .withPassword(config.redisPassword.toCharArray())
            .withTimeout(Duration.ofSeconds(10))
            .build(),
    )
    val connection = redis.connect()
    val commands = connection.sync()
    try {
        commands.xgroupCreate(
            XReadArgs.StreamOffset.from(QueueNames.SUBMISSIONS_STREAM, "0"),
            QueueNames.ORCHESTRATOR_GROUP,
            XGroupCreateArgs.Builder.mkstream(),
        )
        log.info("Created consumer group {}", QueueNames.ORCHESTRATOR_GROUP)
    } catch (_: RedisBusyException) {
        // BUSYGROUP: the group already exists (another replica or a restart).
    }

    val running = AtomicBoolean(true)
    val stopped = CountDownLatch(1)
    val lastLoop = AtomicReference(Instant.now())

    // Liveness endpoint for the Docker / Kubernetes healthcheck.
    val health = HttpServer.create(InetSocketAddress(config.healthPort), 0).apply {
        createContext("/health") { ex ->
            val alive = Duration.between(lastLoop.get(), Instant.now()) < Duration.ofSeconds(30) && repository.isHealthy()
            val body = """{"status":"${if (alive) "UP" else "DOWN"}","service":"orchestrator","consumer":"${config.consumerName}"}"""
                .toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(if (alive) 200 else 503, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        start()
    }

    // Factor IX: SIGTERM stops taking new jobs, lets the current one finish and
    // only then closes connections. Unacknowledged messages stay in the stream.
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutdown signal received, finishing current job")
            running.set(false)
            stopped.await(20, TimeUnit.SECONDS)
            health.stop(0)
            connection.close()
            redis.shutdown()
            repository.close()
            log.info("Orchestrator stopped")
        },
    )

    log.info("Orchestrator {} is consuming {}", config.consumerName, QueueNames.SUBMISSIONS_STREAM)
    val consumer = Consumer.from(QueueNames.ORCHESTRATOR_GROUP, config.consumerName)
    try {
        while (running.get()) {
            lastLoop.set(Instant.now())
            val messages = try {
                commands.xreadgroup(
                    consumer,
                    XReadArgs.Builder.block(Duration.ofSeconds(5)).count(1),
                    XReadArgs.StreamOffset.lastConsumed(QueueNames.SUBMISSIONS_STREAM),
                )
            } catch (e: Exception) {
                if (!running.get()) break
                log.warn("Failed to read from Redis: {}", e.message)
                Thread.sleep(2_000)
                continue
            }
            for (message in messages) {
                val id = message.body[QueueNames.FIELD_SUBMISSION_ID]
                runCatching { process(UUID.fromString(id), config, repository, runner, s3) }
                    .onFailure { e ->
                        log.error("Submission {} failed with an internal error", id, e)
                        runCatching {
                            repository.finish(UUID.fromString(id), SubmissionStatus.ERROR, null, "Внутрішня помилка перевірки", null)
                        }
                    }
                // ACK only after the result is stored: at-least-once delivery (ADR-0002).
                commands.xack(QueueNames.SUBMISSIONS_STREAM, QueueNames.ORCHESTRATOR_GROUP, message.id)
            }
        }
    } finally {
        stopped.countDown()
    }
}

private fun process(
    id: UUID,
    config: OrchestratorConfig,
    repository: SubmissionRepository,
    runner: SimulatedRunner,
    s3: MinioClient,
) {
    val job = repository.find(id) ?: run {
        log.warn("Submission {} not found, skipping", id)
        return
    }
    if (job.status.isFinal || !repository.markRunning(id)) {
        log.info("Submission {} is already {}, skipping (idempotent redelivery)", id, job.status)
        return
    }
    log.info("Checking submission {} ({})", id, job.language)

    val started = System.currentTimeMillis()
    Thread.sleep(config.simulatedRunMillis.random())
    val report = runner.check(job, durationMs = System.currentTimeMillis() - started)

    val key = "reports/$id.json"
    val bytes = json.encodeToString(report).toByteArray()
    s3.putObject(
        PutObjectArgs.builder()
            .bucket(config.s3Bucket)
            .`object`(key)
            .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
            .contentType("application/json")
            .build(),
    )

    val passed = report.checks.count { it.passed }
    val status = if (passed == report.checks.size) SubmissionStatus.PASSED else SubmissionStatus.FAILED
    val summary = "Пройдено перевірок: $passed з ${report.checks.size}"
    repository.finish(id, status, report.score, summary, key)
    log.info("Submission {} -> {} ({}/{})", id, status, report.score, report.maxScore)
}
