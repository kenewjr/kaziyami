plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":core"))
    compileOnly("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}

val packageHelloJar by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Packages the Kototoro Hello sample into a dual-mode JAR with classes.dex"
    dependsOn(tasks.named("classes"))
    commandLine("python", "${rootProject.rootDir}/tools/package_hello_jar.py")
}

tasks.named("test") {
    dependsOn(packageHelloJar)
}

