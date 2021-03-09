import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    application
}

group = "com.xenecho"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    //implementation("org.openpnp:opencv:4.5.1-0")
    implementation("org.bytedeco:javacv-platform:1.5.4")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "MainKt"
}