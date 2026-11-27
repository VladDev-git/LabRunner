package ua.labrunner.api

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Duration

/**
 * Prometheus metrics of the Core API.
 *
 * RED: `ktor_http_server_requests_seconds_*` (rate, status for errors, histogram for duration),
 * business: submissions created and the backlog of checks waiting in the queue.
 */
class ApiMetrics(
    database: Database,
) {
    // Common tags must be configured before the first meter is registered, otherwise
    // the meters registered earlier silently lose them.
    val registry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
            config().commonTags("service", "api")
        }

    // Not "...created": the Prometheus client reserves the `_created` suffix (OpenMetrics)
    // and would export the counter as `labrunner_submissions_total`.
    private val submissionsAccepted: Counter =
        Counter
            .builder("labrunner.submissions.accepted")
            .description("Submissions accepted by the API")
            .register(registry)

    init {
        // Backlog read from the database on every scrape (every 15 s).
        for (status in listOf("QUEUED", "RUNNING")) {
            Gauge
                .builder("labrunner.submissions.backlog") { database.countByStatus(status).toDouble() }
                .description("Submissions that are not checked yet")
                .tag("status", status.lowercase())
                .register(registry)
        }
    }

    fun submissionAccepted() = submissionsAccepted.increment()

    fun scrape(): String = registry.scrape()
}

fun Application.installMetrics(metrics: ApiMetrics) {
    install(MicrometerMetrics) {
        registry = metrics.registry
        // Histogram buckets make p95 latency computable in Prometheus (histogram_quantile).
        distributionStatisticConfig =
            DistributionStatisticConfig
                .Builder()
                .percentilesHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1).toNanos().toDouble())
                .maximumExpectedValue(Duration.ofSeconds(10).toNanos().toDouble())
                .build()
        meterBinders =
            listOf(
                ClassLoaderMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
                UptimeMetrics(),
            )
    }
}
