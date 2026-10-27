dependencies {
    api("org.jsoup:jsoup:1.23.2")
    api("com.squareup.okhttp3:okhttp:4.12.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/*\$\$serializer*",
                    "**/*\$Companion*"
                )
            }
        })
    )
}
