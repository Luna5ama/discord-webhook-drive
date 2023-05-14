group = "dev.luna5ama"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm").version("1.8.21")
    kotlin("plugin.serialization").version("1.8.21")
}

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion: String by project

    testImplementation(kotlin("test"))

    implementation("org.slf4j:slf4j-simple:2.0.5")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}