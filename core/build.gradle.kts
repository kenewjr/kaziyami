dependencies {
    compileOnly(files("$rootDir/libs/extensions-lib-14.jar"))
    compileOnly(files("$rootDir/build/android-sdk/platforms/android-33/android.jar"))
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("uy.kohesive.injekt:injekt-core:1.16.1")

    api("org.jsoup:jsoup:1.17.2")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    testImplementation(files("$rootDir/libs/extensions-lib-14.jar"))
    testImplementation(files("$rootDir/build/android-sdk/platforms/android-33/android.jar"))
    testImplementation("io.reactivex:rxjava:1.3.8")
    testImplementation("uy.kohesive.injekt:injekt-core:1.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
