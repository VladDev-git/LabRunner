package ua.labrunner.api

/**
 * Twelve-Factor App, factor III: every value that differs between environments
 * is read from environment variables. Nothing environment-specific lives in code.
 */
data class AppConfig(
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbPoolSize: Int,
    val redisHost: String,
    val redisPort: Int,
    val redisPassword: String,
    val s3Endpoint: String,
    val s3AccessKey: String,
    val s3SecretKey: String,
    val s3Bucket: String,
    val version: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun required(name: String) =
                env[name]?.takeIf { it.isNotBlank() }
                    ?: error("Environment variable $name is required")

            fun optional(
                name: String,
                default: String,
            ) = env[name]?.takeIf { it.isNotBlank() } ?: default

            return AppConfig(
                port = optional("PORT", "8080").toInt(),
                dbUrl = required("DB_URL"),
                dbUser = required("DB_USER"),
                dbPassword = required("DB_PASSWORD"),
                dbPoolSize = optional("DB_POOL_SIZE", "10").toInt(),
                redisHost = required("REDIS_HOST"),
                redisPort = optional("REDIS_PORT", "6379").toInt(),
                redisPassword = required("REDIS_PASSWORD"),
                s3Endpoint = required("S3_ENDPOINT"),
                s3AccessKey = required("S3_ACCESS_KEY"),
                s3SecretKey = required("S3_SECRET_KEY"),
                s3Bucket = required("S3_BUCKET"),
                version = optional("APP_VERSION", "0.2.0"),
            )
        }
    }
}
