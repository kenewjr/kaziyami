plugins {
    kotlin("jvm") version "1.8.22" apply false
    kotlin("plugin.serialization") version "1.8.22" apply false
    jacoco
}

allprojects {
    group = "app.kaziyami"
    version = "1.0.0"

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

val validateSourceConfigs by tasks.registering {
    group = "verification"
    description = "Validates source JSON configuration files in sources/ directory"
    doLast {
        val sourcesDir = file("sources")
        if (sourcesDir.exists()) {
            val jsonFiles = sourcesDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (f in jsonFiles) {
                println("[+] Validating source config: ${f.name}")
                val text = f.readText()
                require(text.contains("\"name\"")) { "Validation error in ${f.name}: missing 'name'" }
                require(text.contains("\"lang\"")) { "Validation error in ${f.name}: missing 'lang'" }
                require(text.contains("\"baseUrl\"")) { "Validation error in ${f.name}: missing 'baseUrl'" }
            }
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

tasks.register("check") {
    group = "verification"
    description = "Runs all verification tasks across root and subprojects."
    dependsOn(validateSourceConfigs)
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "check" || task.name == "test" } })
}
