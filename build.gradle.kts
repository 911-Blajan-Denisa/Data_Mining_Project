plugins {
    kotlin("jvm") version "1.9.21"
}

group = "com.data.mining"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.lucene:lucene-core:9.9.1")
    implementation("org.apache.lucene:lucene-queryparser:9.9.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}