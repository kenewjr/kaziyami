dependencies {
    api(project(":core"))
    api(project(":lib-multisrc:generic"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
