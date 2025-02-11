plugins {
    id("build.application")
    id("build.shadow")
}

configurations["shadow"].extendsFrom(configurations["implementation"])

dependencies {

    implementation(libs.midnight.proxy.jwt)
    implementation(libs.apache.httpclient)
    implementation("commons-cli:commons-cli:1.9.0")

}

application {
    mainClass = "org.wallentines.packserver.uploader.Main"
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}

val copyOutputTask = tasks.register<Copy>("copyOutputFiles") {

    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)

    val output = rootDir.resolve("build").resolve("out")

    into(output)
    rename("(.*)\\.jar", "uploader.jar")
}

tasks.build {
    dependsOn(copyOutputTask)
}
