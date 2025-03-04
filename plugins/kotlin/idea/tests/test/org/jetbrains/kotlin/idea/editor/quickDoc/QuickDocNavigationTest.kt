// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.test.TestRoot
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/navigate")
@RunWith(JUnit38ClassRunner::class)
class QuickDocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

    fun testSimple() {
        val target = resolveDocLink("C")
        UsefulTestCase.assertInstanceOf(target, KtClass::class.java)
        Assert.assertEquals("C", (target as KtClass).name)
    }

    fun testJdkClass() {
        val target = resolveDocLink("ArrayList")
        UsefulTestCase.assertInstanceOf(target, PsiClass::class.java)
        Assert.assertEquals("ArrayList", (target as PsiClass).name)
    }

    fun testStdlibFunction() {
        val target = resolveDocLink("reader")
        UsefulTestCase.assertInstanceOf(target, KtFunction::class.java)
        Assert.assertEquals("reader", (target as KtFunction).name)

        val secondaryTarget = KotlinDocumentationProvider().getDocumentationElementForLink(
            myFixture.psiManager, "InputStream", target
        )
        UsefulTestCase.assertInstanceOf(secondaryTarget, PsiClass::class.java)
        Assert.assertEquals("InputStream", (secondaryTarget as PsiClass).name)
    }

    fun testQualifiedName() {
        val target = resolveDocLink("a.b.c.D")
        UsefulTestCase.assertInstanceOf(target, KtClass::class.java)
        Assert.assertEquals("D", (target as KtClass).name)
    }

    fun testTopLevelFun() {
        val target = resolveDocLink("doc.topLevelFun")
        UsefulTestCase.assertInstanceOf(target, KtFunction::class.java)
        Assert.assertEquals("topLevelFun", (target as KtFunction).name)
    }

    fun testTopLevelProperty() {
        val target = resolveDocLink("doc.topLevelProperty")
        UsefulTestCase.assertInstanceOf(target, KtProperty::class.java)
        Assert.assertEquals("topLevelProperty", (target as KtProperty).name)
    }

    private fun resolveDocLink(linkText: String): PsiElement? {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val source = myFixture.elementAtCaret.getParentOfType<KtDeclaration>(false)
        return KotlinDocumentationProvider().getDocumentationElementForLink(
            myFixture.psiManager, linkText, source
        )
    }
}
