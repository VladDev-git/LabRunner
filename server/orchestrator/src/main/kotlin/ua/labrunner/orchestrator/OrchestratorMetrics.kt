package ua.labrunner.orchestrator

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import ua.labrunner.contracts.SubmissionStatus
import java.time.Duration

/** Prometheus metrics of the Runner Orchestrator, exposed on the health port at /metrics. */
class OrchestratorMetrics {
    // Common tags before the first meter, so the timer and counters below get them too.
    val registry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
            config().commonTags("service", "orchestrator")
        }

    private val checkDuration: Timer =
        Timer
            .builder("labrunner.check.duration")
            .description("Time to check one submission")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(100))
            .maximumExpectedValue(Duration.ofSeconds(60))
            .register(registry)

    private val checks =
        SubmissionStatus.entries.filter { it.isFinal }.associateWith { status ->
            Counter
                .builder("labrunner.checks")
                .description("Finished checks by result")
                .tag("result", status.name.lowercase())
                .register(registry)
        }

    init {
        listOf(JvmMemoryMetrics(), JvmGcMetrics(), JvmThreadMetrics(), ProcessorMetrics(), UptimeMetrics())
            .forEach { it.bindTo(registry) }
    }

    fun recordCheck(
        result: SubmissionStatus,
        duration: Duration,
    ) {
        checks[result]?.increment()
        checkDuration.record(duration)
    }

    fun scrape(): String = registry.scrape()
}
