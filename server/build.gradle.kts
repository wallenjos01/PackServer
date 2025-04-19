plugins {
    id("build.application")
    id("build.shadow")
}

configurations["shadow"].extendsFrom(configurations["implementation"])

dependencies {

    implementation(libs.jwtutil)

    implementation(libs.netty.buffer)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.codec.http2)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)
    implementation(libs.netty.transport.epoll)
    implementation(libs.netty.transport.kqueue)

    implementation(libs.slf4j.api)
    implementation(libs.logback.core)
    implementation(libs.logback.classic)

}

application {
    mainClass = "org.wallentines.packserver.Main"
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}

val copyOutputTask = tasks.register<Copy>("copyOutputFiles") {

    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)

    val output = rootDir.resolve("build").resolve("out")

    into(output)
    rename("(.*)\\.jar", "packserver.jar")
}

tasks.build {
    dependsOn(copyOutputTask)
}
