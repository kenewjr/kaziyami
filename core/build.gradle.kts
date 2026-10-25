dependencies {
    api("org.jsoup:jsoup:1.17.2")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
