val extName = "NontonHentai"
val extClass = "eu.kanade.tachiyomi.animeextension.id.nontonhentai.NontonHentai"
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
    implementation(project(":lib-multisrc:animestream"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
