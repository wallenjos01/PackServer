import buildlogic.Utils

plugins {
    id("build.library")
}

Utils.setupResources(project, rootProject, "plugin.json")

dependencies {
    implementation(libs.midnight.proxy)
    implementation(libs.slf4j.api)
}