val extName = "NontonHentai"
val extClass = "eu.kanade.tachiyomi.animeextension.id.nontonhentai.NontonHentai"
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
    implementation(project(":lib-multisrc:animestream"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
