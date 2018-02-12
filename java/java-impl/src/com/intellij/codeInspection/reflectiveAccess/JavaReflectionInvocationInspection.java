// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionInvocationInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final String JAVA_LANG_REFLECT_METHOD = "java.lang.reflect.Method";
  private static final String JAVA_LANG_REFLECT_CONSTRUCTOR = "java.lang.reflect.Constructor";

  private static final String INVOKE = "invoke";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);

        if (isCallToMethod(methodCall, JAVA_LANG_REFLECT_METHOD, INVOKE)) {
          checkReflectionCall(methodCall, 1, holder, JavaReflectionInvocationInspection::isReflectionMethod);
        }
        else if (isCallToMethod(methodCall, JAVA_LANG_REFLECT_CONSTRUCTOR, NEW_INSTANCE)) {
          checkReflectionCall(methodCall, 0, holder, JavaReflectionInvocationInspection::isReflectionConstructor);
        }
      }
    };
  }

  private static boolean isReflectionMethod(PsiMethodCallExpression callExpression) {
    return isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_METHOD) ||
           isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_DECLARED_METHOD);
  }

  private static boolean isReflectionConstructor(PsiMethodCallExpression callExpression) {
    return isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_CONSTRUCTOR) ||
           isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_DECLARED_CONSTRUCTOR);
  }

  private static void checkReflectionCall(@NotNull PsiMethodCallExpression methodCall,
                                          int argumentOffset,
                                          @NotNull ProblemsHolder holder,
                                          @NotNull Predicate<PsiMethodCallExpression> methodPredicate) {
    final List<PsiExpression> requiredTypes =
      getRequiredMethodArguments(methodCall.getMethodExpression().getQualifierExpression(), argumentOffset, methodPredicate);
    if (requiredTypes != null) {
      final PsiExpressionList argumentList = methodCall.getArgumentList();
      final Arguments actualArguments = getActualMethodArguments(argumentList.getExpressions(), argumentOffset, true);
      if (actualArguments != null) {
        if (requiredTypes.size() != actualArguments.expressions.length) {
          if (actualArguments.varargAsArray) {
            final PsiExpression[] expressions = argumentList.getExpressions();
            final PsiElement element = expressions.length == argumentOffset + 1 ? expressions[argumentOffset] : argumentList;
            holder.registerProblem(element, InspectionsBundle.message(
              "inspection.reflection.invocation.item.count", requiredTypes.size()));
          }
          else {
            holder.registerProblem(argumentList, InspectionsBundle.message(
              "inspection.reflection.invocation.argument.count", requiredTypes.size() + argumentOffset));
          }
          return;
        }

        for (int i = 0; i < requiredTypes.size(); i++) {
          final ReflectiveType requiredType = getReflectiveType(requiredTypes.get(i));
          if (requiredType != null) {
            final PsiExpression argument = actualArguments.expressions[i];
            final PsiType actualType = argument.getType();
            if (actualType != null && !requiredType.isAssignableFrom(actualType)) {
              if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
                // either varargs or in-place arguments array
                holder.registerProblem(argument, InspectionsBundle.message(actualArguments.varargAsArray
                                                                           ? "inspection.reflection.invocation.item.not.assignable"
                                                                           : "inspection.reflection.invocation.argument.not.assignable",
                                                                           requiredType.getQualifiedName()));
              }
              else {
                // arguments array in a variable
                final PsiExpression[] expressions = argumentList.getExpressions();
                final PsiElement element = expressions.length == argumentOffset + 1 ? expressions[argumentOffset] : argumentList;
                holder.registerProblem(element, InspectionsBundle.message(
                  "inspection.reflection.invocation.array.not.assignable", actualArguments.expressions.length));
                break;
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  private static List<PsiExpression> getRequiredMethodArguments(@Nullable PsiExpression qualifier,
                                                                int argumentOffset,
                                                                @NotNull Predicate<PsiMethodCallExpression> methodPredicate) {
    final PsiExpression definition = findDefinition(ParenthesesUtils.stripParentheses(qualifier));
    if (definition instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression definitionCall = (PsiMethodCallExpression)definition;
      if (methodPredicate.test(definitionCall)) {
        return JavaLangClassMemberReference.getReflectionMethodArguments(definitionCall, argumentOffset);
      }
    }
    return null;
  }

  @Nullable
  static Arguments getActualMethodArguments(PsiExpression[] arguments, int argumentOffset, boolean allowVarargAsArray) {
    if (allowVarargAsArray && arguments.length == argumentOffset + 1) {
      final PsiExpression[] expressions = getVarargAsArray(arguments[argumentOffset]);
      if (expressions != null) {
        return new Arguments(expressions, true);
      }
    }
    if (arguments.length >= argumentOffset) {
      final PsiExpression[] expressions = argumentOffset != 0 ? Arrays.copyOfRange(arguments, argumentOffset, arguments.length) : arguments;
      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression castOperand = unwrapDisambiguatingCastToObject(expressions[i]);
        if (castOperand != null) {
          expressions[i] = castOperand;
        }
      }
      return new Arguments(expressions, false);
    }
    return null;
  }

  @Nullable
  private static PsiExpression unwrapDisambiguatingCastToObject(@Nullable PsiExpression expression) {
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCast = (PsiTypeCastExpression)expression;
      final PsiTypeElement castElement = typeCast.getCastType();
      if (castElement != null && castElement.getType().equalsToText(JAVA_LANG_OBJECT)) {
        return typeCast.getOperand();
      }
    }
    return null;
  }

  static class Arguments {
    final PsiExpression[] expressions;
    final boolean varargAsArray;

    public Arguments(PsiExpression[] expressions, boolean varargAsArray) {
      this.expressions = expressions;
      this.varargAsArray = varargAsArray;
    }
  }
}
