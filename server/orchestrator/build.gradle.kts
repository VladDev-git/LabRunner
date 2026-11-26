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
    applicationName = "orchestrator"
    mainClass.set("ua.labrunner.orchestrator.MainKt")
}

// Runtime dependencies are pinned in gradle.lockfile (reproducible builds, SCA by Trivy).
configurations.runtimeClasspath {
    resolutionStrategy.activateDependencyLocking()
}

dependencies {
    implementation(project(":shared:contracts"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.lettuce)
    implementation(libs.minio)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    // Gradle 9 no longer adds the JUnit Platform launcher automatically.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
