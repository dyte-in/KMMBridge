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

package co.touchlab.kmmbridge.dependencymanager

import co.touchlab.kmmbridge.TASK_GROUP_NAME
import co.touchlab.kmmbridge.internal.cocoapods
import co.touchlab.kmmbridge.internal.kmmBridgeExtension
import co.touchlab.kmmbridge.internal.kotlin
import co.touchlab.kmmbridge.internal.layoutBuildDir
import co.touchlab.kmmbridge.internal.urlFile
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

internal sealed class SpecRepo {
    object Trunk : SpecRepo()
    class Private(val url: String) : SpecRepo()
}

internal class CocoapodsDependencyManager(
    private val specRepoDeferred: () -> SpecRepo,
    private val allowWarnings: Boolean,
    private val verboseErrors: Boolean
) : DependencyManager {
    override fun configure(
        providers: ProviderFactory,
        project: Project,
        version: String,
        uploadTask: TaskProvider<Task>,
        publishRemoteTask: TaskProvider<Task>
    ) {

        val podSpecFile =
            project.file("${project.layoutBuildDir}/kmmbridge/podspec/${project.kmmBridgeExtension.buildType.get().name.lowercase()}/${project.kotlin.cocoapods.name}.podspec")

        val generatePodspecTask = project.tasks.register("generateReleasePodspec") {
            inputs.files(project.urlFile)
            outputs.file(podSpecFile)
            dependsOn(uploadTask)

            val cocoapodsExtension = project.kotlin.cocoapods
            val urlFileLocal = project.urlFile
            val frameworkName = findFrameworkName(project)

            @Suppress("ObjectLiteralToLambda")
            doLast(object : Action<Task> {
                override fun execute(t: Task) {
                    generatePodspec(
                        cocoapodsExtension, urlFileLocal, version, podSpecFile, frameworkName
                    )
                }
            })
        }

        val pushRemotePodspecTask = project.tasks.register("pushRemotePodspec") {
            group = TASK_GROUP_NAME
            inputs.files(podSpecFile)
            dependsOn(generatePodspecTask)
            outputs.upToDateWhen { false } // We want to always upload when this task is called

            @Suppress("ObjectLiteralToLambda")
            doLast(object : Action<Task> {
                override fun execute(t: Task) {
                    val extras = mutableListOf<String>()

                    if (allowWarnings) {
                        extras.add("--allow-warnings")
                    }

                    if (verboseErrors) {
                        extras.add("--verbose")
                    }

                    when (val specRepo = specRepoDeferred()) {
                        is SpecRepo.Trunk -> {
                            providers.exec {
                                commandLine(
                                    "pod", "trunk", "push", podSpecFile, *extras.toTypedArray()
                                )
                            }.standardOutput.asText.get()
                        }

                        is SpecRepo.Private -> {
                            val execOutput = providers.exec {
                                commandLine(
                                    "pod",
                                    "repo",
                                    "push",
                                    specRepo.url,
                                    podSpecFile,
                                    *extras.toTypedArray()
                                )
                            }
                            logger.info(execOutput.standardOutput.asText.get())
                            val errorOut = execOutput.standardError.asText.get()
                            val anyError = errorOut.lines().filter { it.isNotBlank() }.isNotEmpty()
                            if(anyError){
                                logger.error(errorOut)
                            }
                        }
                    }
                }
            })
        }

        publishRemoteTask.configure {
            dependsOn(pushRemotePodspecTask)
        }
    }

    override val needsGitTags: Boolean = false
}

private fun findFrameworkName(project: Project): org.gradle.api.provider.Provider<String> {
    val anyPodFramework = project.provider {
        val anyTarget = project.kotlin.targets
            .withType(KotlinNativeTarget::class.java)
            .matching { it.konanTarget.family.isAppleFamily }.first()
        val anyFramework = anyTarget.binaries
            .matching { it.name.startsWith(KotlinCocoapodsPlugin.POD_FRAMEWORK_PREFIX) }
            .withType(Framework::class.java)
            .first()
        anyFramework
    }
    return anyPodFramework.map { it.baseName }
}

// Adapted from spec generation logic in the kotlin.cocoapods plugin, but we skip script phases and some other details,
// and we read straight from the project and cocoapods extension rather than task properties. We also ignore source and
// insert our deploy URL instead, and include our own version logic.
// TODO it might be nice to migrate this back to using the kotlin.cocoapods podspec task directly, but not worth the
//  effort to wire it up right now.
private fun generatePodspec(
    cocoapodsExtension: CocoapodsExtension,
    urlFile: File,
    projectVersion:String,
    outputFile: File,
    frameworkName: Provider<String>
) = with(cocoapodsExtension) {
    val deploymentTargets = run {
        listOf(ios, osx, tvos, watchos).filter { it.deploymentTarget != null }.joinToString("\n") {
            if (extraSpecAttributes.containsKey("${it.name}.deployment_target")) "" else "|    spec.${it.name}.deployment_target = '${it.deploymentTarget}'"
        }
    }

    val dependencies = pods.joinToString(separator = "\n") { pod ->
        val versionSuffix = if (pod.version != null) ", '${pod.version}'" else ""
        "|    spec.dependency '${pod.name}'$versionSuffix"
    }

    val vendoredFramework = "${frameworkName.get()}.xcframework"
    val vendoredFrameworks =
        if (extraSpecAttributes.containsKey("vendored_frameworks")) "" else "|    spec.vendored_frameworks      = '$vendoredFramework'"

    val libraries =
        if (extraSpecAttributes.containsKey("libraries")) "" else "|    spec.libraries                = 'c++'"

    val customSpec = extraSpecAttributes.map { "|    spec.${it.key} = ${it.value}" }.joinToString("\n")

    val url = urlFile.readText()
    val version = version ?: projectVersion

    // 'Accept: application/octet-stream' needed for GitHub release file downloads
    outputFile.writeText(
        """
            |Pod::Spec.new do |spec|
            |    spec.name                     = '$name'
            |    spec.version                  = '$version'
            |    spec.homepage                 = ${homepage.orEmpty().surroundWithSingleQuotesIfNeeded()}
            |    spec.source                   = { 
            |                                      :http => '${url}',
            |                                      :type => 'zip',
            |                                      :headers => ["'Accept: application/octet-stream'"]
            |                                    }
            |    spec.authors                  = ${authors.orEmpty().surroundWithSingleQuotesIfNeeded()}
            |    spec.license                  = ${license.orEmpty().surroundWithSingleQuotesIfNeeded()}
            |    spec.summary                  = '${summary.orEmpty()}'
            $vendoredFrameworks
            $libraries
            $deploymentTargets
            $dependencies
            $customSpec
            |end
        """.trimMargin()
    )
}

private fun String.surroundWithSingleQuotesIfNeeded(): String =
    if (startsWith("{") || startsWith("<<-") || startsWith("'")) this else "'$this'"
