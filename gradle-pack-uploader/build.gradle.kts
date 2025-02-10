plugins {
    `java-gradle-plugin`
    id("build.library")
    id("maven-publish")
}

gradlePlugin {
    val packUploader by plugins.creating {
        id = "org.wallentines.gradle-pack-uploader"
        implementationClass = "org.wallentines.gradle.packserver.PackUploaderPlugin"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation("net.fabricmc:fabric-loom:1.9-SNAPSHOT")
    implementation(libs.midnight.config)
    implementation(libs.midnight.config.json)
    implementation(libs.apache.httpclient)
}


publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = "gradle-pack-uploader"
        from(components["java"])
    }

    if (project.hasProperty("pubUrl")) {

        val url: String = project.properties["pubUrl"] as String
        repositories.maven(url) {
            name = "pub"
            credentials(PasswordCredentials::class.java)
        }
    }
}
