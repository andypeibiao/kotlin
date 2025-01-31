/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import groovy.lang.Closure
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import org.jetbrains.kotlin.compilerRunner.konanVersion
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesClientSettings
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesTestExecutionSpec
import org.jetbrains.kotlin.gradle.targets.native.internal.parseKotlinNativeStackTraceAsJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.MetaVersion
import java.io.File

open class KotlinNativeTest : KotlinTest() {
    @Suppress("LeakingThis")
    private val processOptions: ProcessForkOptions = DefaultProcessForkOptions(fileResolver)

    @InputFile
    @SkipWhenEmpty
    val executableProperty: Property<File> = project.objects.property(File::class.java)

    @Input
    var args: List<String> = emptyList()

    // Already taken into account in the executableProperty.
    @get:Internal
    var executable: File
        get() = executableProperty.get()
        set(value) {
            executableProperty.set(value)
        }

    @get:Input
    var workingDir: String
        get() = processOptions.workingDir.canonicalPath
        set(value) {
            processOptions.workingDir = File(value)
        }

    @get:Input
    var environment: Map<String, Any>
        get() = processOptions.environment
        set(value) {
            processOptions.environment = value
        }

    private fun <T> Property<T>.set(providerLambda: () -> T) = set(project.provider { providerLambda() })

    fun executable(file: File) {
        executableProperty.set(file)
    }

    fun executable(path: String) {
        executableProperty.set { project.file(path) }
    }

    fun executable(provider: () -> File) {
        executableProperty.set(provider)
    }

    fun executable(provider: Closure<File>) {
        executableProperty.set(project.provider(provider))
    }

    fun environment(name: String, value: Any) {
        processOptions.environment(name, value)
    }

    // KonanVersion doesn't provide an API to compare versions,
    // so we have to transform it to KotlinVersion first.
    // Note: this check doesn't take into account the meta version (release, eap, dev).
    private fun KonanVersion.isAtLeast(major: Int, minor: Int, patch: Int): Boolean =
        KotlinVersion(this.major, this.minor, this.maintenance).isAtLeast(major, minor, patch)

    override fun createTestExecutionSpec(): TCServiceMessagesTestExecutionSpec {
        val extendedForkOptions = DefaultProcessForkOptions(fileResolver)
        processOptions.copyTo(extendedForkOptions)
        extendedForkOptions.executable = executable.absolutePath

        val clientSettings = TCServiceMessagesClientSettings(
            name,
            testNameSuffix = targetName,
            prependSuiteName = targetName != null,
            treatFailedTestOutputAsStacktrace = false,
            stackTraceParser = ::parseKotlinNativeStackTraceAsJvm
        )

        // The KotlinTest expects that the exit code is zero even if some tests failed.
        // In this case it can check exit code and distinguish test failures from crashes.
        // But K/N allows forcing a zero exit code only since 1.3 (which was included in Kotlin 1.3.40).
        // Thus we check the exit code only for newer versions.
        val checkExitCode = project.konanVersion.isAtLeast(1, 3, 0)

        val cliArgs = CliArgs("TEAMCITY", checkExitCode, includePatterns, excludePatterns, args)

        return TCServiceMessagesTestExecutionSpec(
            extendedForkOptions,
            cliArgs.toList(),
            checkExitCode,
            clientSettings
        )
    }

    private class CliArgs(
        val testLogger: String? = null,
        val checkExitCode: Boolean = true,
        val testGradleFilter: Set<String> = setOf(),
        val testNegativeGradleFilter: Set<String> = setOf(),
        val userArgs: List<String> = emptyList()
    ) {
        fun toList() = mutableListOf<String>().also {

            if (checkExitCode) {
                // Avoid returning a non-zero exit code in case of failed tests.
                it.add("--ktest_no_exit_code")
            }

            if (testLogger != null) {
                it.add("--ktest_logger=$testLogger")
            }

            if (testGradleFilter.isNotEmpty()) {
                it.add("--ktest_gradle_filter=${testGradleFilter.joinToString(",")}")
            }

            if (testNegativeGradleFilter.isNotEmpty()) {
                it.add("--ktest_negative_gradle_filter=${testNegativeGradleFilter.joinToString(",")}")
            }

            it.addAll(userArgs)
        }
    }
}
