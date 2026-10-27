dependencies {
    api("org.jsoup:jsoup:1.17.2")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
