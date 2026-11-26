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
    applicationName = "api"
    mainClass.set("ua.labrunner.api.ApplicationKt")
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
}
