plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.5")
    implementation("net.fabricmc:fabric-loom:1.9-SNAPSHOT")
}
