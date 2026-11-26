import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "ua.labrunner"
version = "0.2.0"

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

dependencies {
    implementation(project(":shared:contracts"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.lettuce)
    implementation(libs.minio)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    // Gradle 9 no longer adds the JUnit Platform launcher automatically.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
