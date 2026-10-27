plugins {
    kotlin("jvm") version "2.4.21" apply false
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

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            allWarningsAsErrors = true
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
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true
            showStandardStreams = false
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

tasks.register<Exec>("assembleAniyomi") {
    group = "distribution"
    description = "Builds Aniyomi APKs and generates repo/ index files."
    dependsOn(tasks.named("assembleRelease"))
    commandLine("python", "tools/generate_repo_index.py", "--mode", "aniyomi")
}

tasks.register<Exec>("assembleDebugAll") {
    group = "build"
    description = "Assembles debug APKs for all extensions into build/kaziyami-debug/."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    commandLine("python", "tools/build_apks.py", "--debug-dir", "build/kaziyami-debug")
}

tasks.register<Exec>("generateRepoIndex") {
    group = "distribution"
    description = "Generates repository indexes for both Aniyomi and Kototoro."
    commandLine("python", "tools/generate_repo_index.py", "--mode", "all")
}

tasks.register<Exec>("packageKototoroPlugin") {
    group = "distribution"
    description = "Packages all Kaziyami sources into a Kototoro-compatible plugin JAR."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    commandLine("python", "tools/package_kototoro_plugin.py")
}

tasks.register<Exec>("packageJar") {
    group = "distribution"
    description = "Packages Kototoro JAR plugins and generates repo/jar/ repository indexes."
    dependsOn(tasks.named("packageKototoroPlugin"))
    commandLine("python", "tools/generate_repo_index.py", "--mode", "kototoro")
}

tasks.register("assembleAll") {
    group = "distribution"
    description = "Builds both Variant A (Aniyomi APKs) and Variant B (Kototoro JARs) with repo indexes."
    dependsOn(tasks.named("assembleAniyomi"))
    dependsOn(tasks.named("packageJar"))
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

val verifyJars by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates Kototoro plugin JARs and repository index against the format contract."
    dependsOn(tasks.named("packageJar"))
    dependsOn(":samples:kototoro-hello:packageHelloJar")
    commandLine(
        "python", "tools/verify_jars.py",
        "--jars",
        "samples/kototoro-hello/build/libs/kaziyami-hello.jar",
        "repo/jar/apk/kaziyami-all-v1.4.2.jar",
        "--repo-dir", "repo/jar"
    )
}

tasks.register("check") {
    group = "verification"
    description = "Runs all verification tasks across root and subprojects."
    dependsOn(validateSourceConfigs)
    dependsOn(checkExtensionIcons)
    dependsOn(testVerifyApks)
    dependsOn(verifyApks)
    dependsOn(verifyJars)
    dependsOn(tasks.named("packageJar"))
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "check" || task.name == "test" } })
}

project(":core") {
    tasks.named("test") {
        dependsOn(rootProject.tasks.named("packageKototoroPlugin"))
    }
}
