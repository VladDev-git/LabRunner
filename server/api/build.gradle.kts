import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "ua.labrunner"
version = "0.3.0"

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "api"
    mainClass.set("ua.labrunner.api.ApplicationKt")
}

// Runtime dependencies are pinned in gradle.lockfile: reproducible images and
// a manifest that Trivy can scan for known vulnerabilities (SCA).
configurations.runtimeClasspath {
    resolutionStrategy.activateDependencyLocking()
}

dependencies {
    implementation(project(":shared:contracts"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.lettuce)
    implementation(libs.minio)
    implementation(libs.logback)

    // Security: raise vulnerable transitive dependencies (Netty from Ktor/Lettuce,
    // Jackson, BouncyCastle and Guava from the MinIO SDK, snappy from Lettuce).
    implementation(platform(libs.netty.bom))
    implementation(platform(libs.jackson.bom))
    constraints {
        implementation(libs.bouncycastle.bcprov) { because("CVE-2025-14813 and others, fixed in 1.85+") }
        implementation(libs.guava) { because("vulnerable versions come from the MinIO SDK") }
        implementation(libs.snappy.java) { because("CVE-2026-93451, CVE-2026-108106") }
    }

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
