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
    id("com.gradle.plugin-publish") version "1.0.0"
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://github.com/touchlab/KMMBridge"
    vcsUrl = "https://github.com/touchlab/KMMBridge.git"
    description =
        "KMMBridge is a set of Gradle tooling that facilitates publishing and consuming pre-built KMM (Kotlin Multiplatform Mobile) Xcode Framework binaries."
    plugins {
        register("kmmbridge-test-plugin") {
            id = "co.touchlab.kmmbridge.test"
            implementationClass = "co.touchlab.kmmbridge.test.KMMBridgeTestPlugin"
            displayName = "KMMBridge/Test"
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
    implementation(libs.okhttp)
    implementation(libs.gson)
    api(project(":kmmbridge"))

    testImplementation(kotlin("test"))
}