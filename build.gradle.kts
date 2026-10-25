plugins {
    kotlin("jvm") version "1.8.22" apply false
    kotlin("plugin.serialization") version "1.8.22" apply false
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "jacoco")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            excludeTags("live")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.register<Test>("liveSmokeTest") {
        group = "verification"
        description = "Runs live integration tests against real sites."
        useJUnitPlatform {
            includeTags("live")
        }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

tasks.register<Test>("liveSmokeTest") {
    group = "verification"
    description = "Runs live integration tests against real sites."
    useJUnitPlatform {
        includeTags("live")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Exec>("generateRepoIndex") {
    group = "distribution"
    description = "Generates Aniyomi repo index.json and index.min.json."
    commandLine("python", "tools/generate_repo_index.py")
}
