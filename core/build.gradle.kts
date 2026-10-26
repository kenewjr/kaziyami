dependencies {
    api("org.jsoup:jsoup:1.23.2")
    api("com.squareup.okhttp3:okhttp:5.5.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
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
