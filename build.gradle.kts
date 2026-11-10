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

fun findAndroidJar(): File {
    val localJar = file("$rootDir/build/android-sdk/platforms/android-33/android.jar")
    if (localJar.exists()) return localJar

    val envRoots = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "/usr/local/lib/android/sdk",
        "/opt/android-sdk"
    )
    for (root in envRoots) {
        for (api in listOf("android-33", "android-34", "android-32", "android-31")) {
            val f = File(root, "platforms/$api/android.jar")
            if (f.exists()) return f
        }
    }
    return localJar
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "jacoco")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }

    dependencies {
        val androidJar = findAndroidJar()
        add("compileOnly", files("$rootDir/libs/extensions-lib-14.jar"))
        add("compileOnly", files(androidJar))
        add("compileOnly", "io.reactivex:rxjava:1.3.8")
        add("compileOnly", "uy.kohesive.injekt:injekt-core:1.16.1")

        if (name != "core") {
            add("testImplementation", files("$rootDir/core/build/classes/kotlin/test"))
        }
        add("testImplementation", files("$rootDir/libs/extensions-lib-14.jar"))
        add("testImplementation", files(androidJar))
        add("testImplementation", "io.reactivex:rxjava:1.3.8")
        add("testImplementation", "uy.kohesive.injekt:injekt-core:1.16.1")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            allWarningsAsErrors = false
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    tasks.withType<Test> {
        dependsOn(":core:compileTestKotlin")
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
            "src/id/nontonhentai",
            "src/en/hentaimama",
            "src/en/hanime",
            "src/en/rulevid",
            "src/en/rule34video",
            "src/zh/hanime1",
            "src/zh/3xchina"
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

tasks.register("bump") {
    group = "versioning"
    description = "Bumps a single extension version: ./gradlew bump -Psource=<slug>"
    doLast {
        val src = project.findProperty("source") as? String
            ?: throw GradleException("Specify source: ./gradlew bump -Psource=<slug>")
        project.exec {
            commandLine("python", "tools/bump_source.py", src)
        }
    }
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
    description = "Generates repository index for Aniyomi."
    commandLine("python", "tools/generate_repo_index.py", "--mode", "aniyomi")
}

val prepareHostClasspath by tasks.registering(Copy::class) {
    group = "verification"
    description = "Prepares host runtime dependency jars for API binding verification."
    val hostConf = configurations.maybeCreate("hostClasspath")
    dependencies {
        add("hostClasspath", "com.squareup.okhttp3:okhttp:4.9.3")
        add("hostClasspath", "com.squareup.okio:okio:3.18.2")
        add("hostClasspath", "io.reactivex:rxjava:1.3.8")
        add("hostClasspath", "uy.kohesive.injekt:injekt-core:1.16.1")
        add("hostClasspath", "org.jsoup:jsoup:1.15.3")
        add("hostClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")
        add("hostClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
        add("hostClasspath", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
    }
    from(hostConf)
    into(file("$buildDir/host-libs"))
}

val testVerifyApks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs unit tests for APK verification logic with fixtures."
    commandLine("python", "tools/test_verify_apks.py")
}

val testVerifyHostApiBinding by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs unit tests for host API binding verification with fixtures."
    dependsOn(prepareHostClasspath)
    commandLine("python", "tools/test_verify_host_api_binding.py")
}

val verifyApks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies built extension APKs for signing, Dalvik bytecode, binary manifest, and metadata."
    dependsOn(tasks.named("assembleRelease"))
    commandLine("python", "tools/verify_apks.py", "--allow-debug-cert")
}

val verifyNoHostClassesPackaged by tasks.registering(Exec::class) {
    group = "verification"
    description = "Asserts no host-provided classes are bundled into built extension APKs."
    dependsOn(tasks.named("assembleRelease"))
    commandLine("python", "tools/verify_no_host_classes.py")
}

val verifyHostApiBinding by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies binary compatibility of all method/field calls against host classpath."
    dependsOn(tasks.named("assembleRelease"))
    dependsOn(prepareHostClasspath)
    commandLine("python", "tools/verify_host_api_binding.py")
}

val testSourceInstantiation by tasks.registering(Exec::class) {
    group = "verification"
    description = "Tests reflection instantiation of sources against built extension artifacts."
    dependsOn(tasks.named("assembleRelease"))
    dependsOn(prepareHostClasspath)
    dependsOn(":core:compileTestKotlin")
    commandLine("python", "tools/test_source_instantiation.py")
}

val kototoroSelfTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs faithful replica of Kototoro's in-app SourceTester test harness."
    dependsOn(tasks.named("assembleRelease"))
    dependsOn(prepareHostClasspath)
    dependsOn(":core:compileTestKotlin")
    val isLive = project.hasProperty("live")
    if (isLive) {
        commandLine("python", "tools/kototoro_self_test.py", "--live")
    } else {
        commandLine("python", "tools/kototoro_self_test.py")
    }
}

tasks.register("check") {
    group = "verification"
    description = "Runs all verification tasks across root and subprojects."
    dependsOn(validateSourceConfigs)
    dependsOn(checkExtensionIcons)
    dependsOn(testVerifyApks)
    dependsOn(testVerifyHostApiBinding)
    dependsOn(verifyApks)
    dependsOn(verifyNoHostClassesPackaged)
    dependsOn(verifyHostApiBinding)
    dependsOn(testSourceInstantiation)
    dependsOn(kototoroSelfTest)
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "check" || task.name == "test" } })
}
