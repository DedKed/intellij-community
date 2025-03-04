// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.expressionVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.isDynamic
import org.jetbrains.kotlin.types.typeUtil.isNullableAny

class UnsafeCastFromDynamicInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        expressionVisitor(fun(expression) {
            val context = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
            val expectedType = context[BindingContext.EXPECTED_EXPRESSION_TYPE, expression] ?: return
            val actualType = expression.getType(context) ?: return

            if (actualType.isDynamic() && !expectedType.isDynamic() && !expectedType.isNullableAny() &&
                !TypeUtils.noExpectedType(expectedType)
            ) {
                holder.registerProblem(
                    expression,
                    KotlinBundle.message("implicit.unsafe.cast.from.dynamic.to.0", expectedType),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    CastExplicitlyFix(expectedType)
                )
            }
        })
}

private class CastExplicitlyFix(private val type: KotlinType) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("cast.explicitly.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val expression = descriptor.psiElement as? KtExpression ?: return
        val typeName = type.constructor.declarationDescriptor?.name ?: return
        val pattern = if (type.isMarkedNullable) "$0 as? $1" else "$0 as $1"
        val newExpression = KtPsiFactory(expression).createExpressionByPattern(pattern, expression, typeName)
        expression.replace(newExpression)
    }
}