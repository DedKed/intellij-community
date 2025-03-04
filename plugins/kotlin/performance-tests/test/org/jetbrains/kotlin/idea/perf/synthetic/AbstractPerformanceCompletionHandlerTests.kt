// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.configureWithExtraFile
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.CODE_STYLE_SETTING_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.ELEMENT_TEXT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.INVOCATION_COUNT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.LOOKUP_STRING_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.TAIL_TEXT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.CompletionHandlerTestBase
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.performanceTest
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.util.OutputConfig
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.utils.addToStdlib.indexOfOrNull
import java.io.File

/**
 * inspired by @see AbstractCompletionHandlerTests
 */
abstract class AbstractPerformanceCompletionHandlerTests(
    protected val defaultCompletionType: CompletionType,
    protected val note: String = ""
) : CompletionHandlerTestBase() {

    companion object {
        @JvmStatic
        val statsMap: MutableMap<String, Stats> = mutableMapOf()
    }

    protected open val statsPrefix = "completion"

    protected fun stats(): Stats {
        val suffix = "${defaultCompletionType.toString().lowercase()}${if (note.isNotEmpty()) "-$note" else ""}"
        return statsMap.computeIfAbsent(suffix) {
            Stats("$statsPrefix-$suffix", outputConfig = outputConfig(), profilerConfig = profilerConfig())
        }
    }

    protected open fun profilerConfig(): ProfilerConfig = ProfilerConfig()

    protected open fun outputConfig(): OutputConfig = OutputConfig()

    override fun tearDown() {
        runAll(
            ThrowableRunnable { commitAllDocuments() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected open fun doPerfTest(unused: String) {
        val testPath = testPath()
        setUpFixture(testPath)

        configureCodeStyleAndRun(project) {
            try {
                val fileText = FileUtil.loadFile(File(testPath))
                withCustomCompilerOptions(fileText, project, module) {
                    assertTrue("\"<caret>\" is missing in file \"$testPath\"", fileText.contains("<caret>"))

                    val invocationCount = InTextDirectivesUtils.getPrefixedInt(fileText, INVOCATION_COUNT_PREFIX) ?: 1
                    val lookupString = InTextDirectivesUtils.findStringWithPrefixes(fileText, LOOKUP_STRING_PREFIX)
                    val itemText = InTextDirectivesUtils.findStringWithPrefixes(fileText, ELEMENT_TEXT_PREFIX)
                    val tailText = InTextDirectivesUtils.findStringWithPrefixes(fileText, TAIL_TEXT_PREFIX)
                    val completionChars = completionChars(fileText)

                    val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType

                    val codeStyleSettings = CodeStyle.getSettings(file)
                    val kotlinStyleSettings = codeStyleSettings.kotlinCustomSettings
                    val commonStyleSettings = codeStyleSettings.kotlinCommonSettings
                    for (line in InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, CODE_STYLE_SETTING_PREFIX)) {
                        val index = line.indexOfOrNull('=') ?: error("Invalid code style setting '$line': '=' expected")
                        val settingName = line.substring(0, index).trim()
                        val settingValue = line.substring(index + 1).trim()
                        val (field, settings) = try {
                            kotlinStyleSettings::class.java.getField(settingName) to kotlinStyleSettings
                        } catch (e: NoSuchFieldException) {
                            commonStyleSettings::class.java.getField(settingName) to commonStyleSettings
                        }
                        when (field.type.name) {
                            "boolean" -> field.setBoolean(settings, settingValue.toBoolean())
                            "int" -> field.setInt(settings, settingValue.toInt())
                            else -> error("Unsupported setting type: ${field.type}")
                        }
                    }

                    doPerfTestWithTextLoaded(
                        testPath, completionType, invocationCount, lookupString, itemText, tailText, completionChars,
                    )
                }
            } finally {
                tearDownFixture()
            }
        }
    }

    private fun doPerfTestWithTextLoaded(
        testPath: String,
        completionType: CompletionType,
        time: Int,
        lookupString: String?,
        itemText: String?,
        tailText: String?,
        completionChars: String
    ) {
        performanceTest<Unit, Unit> {
            name(testName())
            stats(stats())
            setUp {
                setUpFixture(testPath)
            }
            test {
                perfTestCore(completionType, time, lookupString, itemText, tailText, completionChars)
            }
            tearDown {
                runWriteAction {
                    myFixture.file.delete()
                }
            }
        }
    }

    private fun testName(): String {
        val javaClass = this.javaClass
        val testName = getTestName(false)
        return if (javaClass.isMemberClass) {
            "${javaClass.simpleName} - $testName"
        } else {
            testName
        }
    }

    protected fun perfTestCore(
        completionType: CompletionType,
        time: Int,
        lookupString: String?,
        itemText: String?,
        tailText: String?,
        completionChars: String
    ) {
        completionChars.let {
            for (idx in 0 until it.length - 1) {
                fixture.type(it[idx])
            }
        }

        fixture.complete(completionType, time)

        if (lookupString != null || itemText != null || tailText != null) {
            val item = getExistentLookupElement(project, lookupString, itemText, tailText)
            if (item != null) {
                selectItem(fixture, item, completionChars.last())
            }
        }
    }

    protected open fun setUpFixture(testPath: String) {
        fixture.configureWithExtraFile(testPath, ".dependency", ".dependency.1", ".dependency.2")
    }

    protected open fun tearDownFixture() {

    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}

abstract class AbstractPerformanceBasicCompletionHandlerTest : AbstractPerformanceCompletionHandlerTests(CompletionType.BASIC)

abstract class AbstractPerformanceSmartCompletionHandlerTest : AbstractPerformanceCompletionHandlerTests(CompletionType.SMART)

abstract class AbstractPerformanceCompletionCharFilterTest : AbstractPerformanceCompletionHandlerTests(
    CompletionType.BASIC,
    note = "charFilter"
)

abstract class AbstractPerformanceKeywordCompletionHandlerTest : AbstractPerformanceCompletionHandlerTests(
    CompletionType.BASIC,
    note = "keyword"
)
