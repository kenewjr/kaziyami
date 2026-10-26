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

tasks.register("liveSmokeTest") {
    group = "verification"
    description = "Runs live integration tests against real sites."
    dependsOn(":core:liveSmokeTest")
}

val checkExtensionIcons by tasks.registering {
    group = "verification"
    description = "Fails if any extension lacks an original Kaziyami icon."
    doLast {
        val extensions = listOf(
            "src/id/nekopoi",
            "src/id/indohentai",
            "src/id/nontonhentai",
            "src/en/hentaimama",
            "src/en/javsubbed",
            "src/en/hanime"
        )
        for (ext in extensions) {
            val xmlIcon = file("$ext/src/main/res/drawable/ic_launcher.xml")
            val name = ext.substringAfterLast('/')
            val pngIcon = file("art/icons/$name.png")
            if (!xmlIcon.exists() && !pngIcon.exists()) {
                throw GradleException("Extension $ext lacks an icon! Missing $xmlIcon")
            }
        }
        println("[+] All 6 extension icons verified.")
    }
}

tasks.register<Exec>("bumpChanged") {
    group = "versioning"
    description = "Bumps extVersionCode for extensions affected by changes."
    commandLine("python", "tools/bump_changed.py", "--bump")
}

tasks.register<Exec>("checkBump") {
    group = "versioning"
    description = "Verifies that extensions affected by changes had their version bumped."
    commandLine("python", "tools/bump_changed.py", "--check")
}

tasks.register<Exec>("assembleRelease") {
    group = "build"
    description = "Assembles and signs release APKs for all extensions, outputting to repo/apk/."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    commandLine("python", "tools/build_apks.py")
}

tasks.register<Exec>("assembleDebugAll") {
    group = "build"
    description = "Assembles debug APKs for all extensions into build/kaziyami-debug/."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    commandLine("python", "tools/build_apks.py", "--debug-dir", "build/kaziyami-debug")
}

tasks.register<Exec>("generateRepoIndex") {
    group = "distribution"
    description = "Generates Aniyomi repo index.json and index.min.json."
    commandLine("python", "tools/generate_repo_index.py")
}

tasks.register<Exec>("packageKototoroPlugin") {
    group = "distribution"
    description = "Packages all Kaziyami sources into a Kototoro-compatible plugin JAR."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    commandLine("python", "tools/package_kototoro_plugin.py")
}

val testVerifyApks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs unit tests for APK verification logic with fixtures."
    commandLine("python", "tools/test_verify_apks.py")
}

val verifyApks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies built extension APKs for signing, Dalvik bytecode, binary manifest, and metadata."
    dependsOn(tasks.named("assembleRelease"))
    commandLine("python", "tools/verify_apks.py", "--allow-debug-cert")
}

tasks.register("check") {
    group = "verification"
    description = "Runs all verification tasks across root and subprojects."
    dependsOn(validateSourceConfigs)
    dependsOn(checkExtensionIcons)
    dependsOn(testVerifyApks)
    dependsOn(verifyApks)
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "check" || task.name == "test" } })
}
