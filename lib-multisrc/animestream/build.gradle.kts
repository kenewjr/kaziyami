dependencies {
    api(project(":core"))
    api(project(":lib-multisrc:generic"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
