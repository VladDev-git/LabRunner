package ua.labrunner.api

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import ua.labrunner.contracts.QueueNames
import java.time.Duration

/** Producer side of the Redis Stream with submissions waiting for a check. */
class SubmissionQueue(config: AppConfig) : AutoCloseable {
    private val log = LoggerFactory.getLogger(SubmissionQueue::class.java)

    private val client: RedisClient = RedisClient.create(
        RedisURI.builder()
            .withHost(config.redisHost)
            .withPort(config.redisPort)
            .withPassword(config.redisPassword.toCharArray())
            .withTimeout(Duration.ofSeconds(5))
            .build(),
    )
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    fun publish(submissionId: String): String =
        connection.sync().xadd(QueueNames.SUBMISSIONS_STREAM, mapOf(QueueNames.FIELD_SUBMISSION_ID to submissionId))

    fun isHealthy(): Boolean = runCatching { connection.sync().ping() == "PONG" }.getOrDefault(false)

    override fun close() {
        log.info("Closing Redis connection")
        connection.close()
        client.shutdown()
    }
}

/** S3-compatible object storage (RustFS locally, Google Cloud Storage in production). */
class ReportStorage(private val config: AppConfig) {
    private val log = LoggerFactory.getLogger(ReportStorage::class.java)

    private val client: MinioClient = MinioClient.builder()
        .endpoint(config.s3Endpoint)
        .region("us-east-1")
        .credentials(config.s3AccessKey, config.s3SecretKey)
        .build()

    fun ensureBucket() {
        val args = BucketExistsArgs.builder().bucket(config.s3Bucket).build()
        if (!client.bucketExists(args)) {
            client.makeBucket(MakeBucketArgs.builder().bucket(config.s3Bucket).build())
            log.info("Created bucket {}", config.s3Bucket)
        }
    }

    fun read(key: String): String? = try {
        client.getObject(GetObjectArgs.builder().bucket(config.s3Bucket).`object`(key).build())
            .use { it.readBytes().decodeToString() }
    } catch (e: ErrorResponseException) {
        if (e.errorResponse().code() == "NoSuchKey") null else throw e
    }
}
