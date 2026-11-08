val extName = "Nekopoi"
val extClass = "eu.kanade.tachiyomi.animeextension.id.nekopoi.Nekopoi"
val extVersionCode = 8
val libVersion = "14"
val extVersion = "14.8"
val isNsfw = true

extra["extName"] = extName
extra["extClass"] = extClass
extra["extVersionCode"] = extVersionCode
extra["libVersion"] = libVersion
extra["extVersion"] = extVersion
extra["isNsfw"] = isNsfw

dependencies {
    implementation(project(":core"))
    implementation(project(":lib-multisrc:generic"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
