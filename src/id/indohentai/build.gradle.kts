val extName = "IndoHentai"
val extClass = "eu.kanade.tachiyomi.animeextension.id.indohentai.IndoHentai"
val extVersionCode = 5
val libVersion = "14"
val extVersion = "14.5"
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
