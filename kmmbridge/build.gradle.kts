@file:Suppress("PropertyName")

/*
* Copyright (c) 2024 Touchlab.
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License
* is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
* or implied. See the License for the specific language governing permissions and limitations under
* the License.
*/

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin)
    id("org.jetbrains.kotlin.plugin.allopen")
    id("java-gradle-plugin")
    alias(libs.plugins.maven.publish)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://github.com/touchlab/KMMBridge"
    vcsUrl = "https://github.com/touchlab/KMMBridge.git"
    description =
        "KMMBridge is a set of Gradle tooling that facilitates publishing and consuming pre-built KMM (Kotlin Multiplatform Mobile) Xcode Framework binaries."
    plugins {
        register("kmmbridge-plugin") {
            id = "io.dyte.kotlin.kmmbridge"
            implementationClass = "co.touchlab.kmmbridge.KMMBridgePlugin"
            displayName = "KMMBridge for Teams"
            tags = listOf(
                "kmm",
                "kotlin",
                "multiplatform",
                "mobile",
                "ios",
                "xcode",
                "framework",
                "binary",
                "publish",
                "consume"
            )
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(kotlin("gradle-plugin"))
    implementation(libs.aws)
    implementation(libs.okhttp)
    implementation(libs.gson)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation("commons-io:commons-io:2.18.0")
}

mavenPublishing {
    publishToMavenCentral(host = com.vanniktech.maven.publish.SonatypeHost.S01, automaticRelease = true)
    val releaseSigningEnabled =
        project.properties["RELEASE_SIGNING_ENABLED"]?.toString()?.equals("false", ignoreCase = true) != true
    if (releaseSigningEnabled) signAllPublications()
    @Suppress("UnstableApiUsage")
    pomFromGradleProperties()
    @Suppress("UnstableApiUsage")
    configureBasedOnAppliedPlugins()
}