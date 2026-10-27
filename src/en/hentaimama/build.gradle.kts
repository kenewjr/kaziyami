val extName = "Hentaimama"
val extClass = "eu.kanade.tachiyomi.animeextension.en.hentaimama.Hentaimama"
val extVersionCode = 2
val libVersion = "14"
val isNsfw = true

extra["extName"] = extName
extra["extClass"] = extClass
extra["extVersionCode"] = extVersionCode
extra["libVersion"] = libVersion
extra["isNsfw"] = isNsfw

dependencies {
    implementation(project(":core"))
    implementation(project(":lib-multisrc:dooplay"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
