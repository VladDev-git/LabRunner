package ua.labrunner.orchestrator

import java.net.InetAddress

/** Factor III: configuration comes only from the environment. */
data class OrchestratorConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val redisHost: String,
    val redisPort: Int,
    val redisPassword: String,
    val s3Endpoint: String,
    val s3AccessKey: String,
    val s3SecretKey: String,
    val s3Bucket: String,
    val consumerName: String,
    val healthPort: Int,
    val simulatedRunMillis: LongRange,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): OrchestratorConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: error("Environment variable $name is required")
            fun optional(name: String, default: String) = env[name]?.takeIf { it.isNotBlank() } ?: default

            return OrchestratorConfig(
                dbUrl = required("DB_URL"),
                dbUser = required("DB_USER"),
                dbPassword = required("DB_PASSWORD"),
                redisHost = required("REDIS_HOST"),
                redisPort = optional("REDIS_PORT", "6379").toInt(),
                redisPassword = required("REDIS_PASSWORD"),
                s3Endpoint = required("S3_ENDPOINT"),
                s3AccessKey = required("S3_ACCESS_KEY"),
                s3SecretKey = required("S3_SECRET_KEY"),
                s3Bucket = required("S3_BUCKET"),
                // Each replica is a separate consumer inside one consumer group.
                consumerName = optional("CONSUMER_NAME", InetAddress.getLocalHost().hostName),
                healthPort = optional("HEALTH_PORT", "8081").toInt(),
                simulatedRunMillis = optional("SIMULATED_RUN_MIN_MS", "1500").toLong()..optional("SIMULATED_RUN_MAX_MS", "3500").toLong(),
            )
        }
    }
}
