val extName = "IndoHentai"
val extClass = "eu.kanade.tachiyomi.animeextension.id.indohentai.IndoHentai"
val extVersionCode = 1
val libVersion = "1.4"
val isNsfw = true

extra["extName"] = extName
extra["extClass"] = extClass
extra["extVersionCode"] = extVersionCode
extra["libVersion"] = libVersion
extra["isNsfw"] = isNsfw

dependencies {
    implementation(project(":core"))
    implementation(project(":lib-multisrc:generic"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
