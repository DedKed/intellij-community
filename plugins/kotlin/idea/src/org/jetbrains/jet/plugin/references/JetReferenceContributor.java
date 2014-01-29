/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.references;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class JetReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(JetSimpleNameExpression.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(new JetSimpleNameReference((JetSimpleNameExpression) element));
                                                }
                                            });

        registrar.registerReferenceProvider(psiElement(JetThisReferenceExpression.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(new JetThisReference((JetThisReferenceExpression) element));
                                                }
                                            });

        registrar.registerReferenceProvider(psiElement(JetArrayAccessExpression.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(new JetArrayAccessReference((JetArrayAccessExpression) element));
                                                }
                                            });

        registrar.registerReferenceProvider(psiElement(JetCallExpression.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(new JetInvokeFunctionReference((JetCallExpression) element));
                                                }
                                            });

        registrar.registerReferenceProvider(psiElement(JetPropertyDelegate.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(
                                                            new JetPropertyDelegationMethodsReference((JetPropertyDelegate) element));
                                                }
                                            });

        registrar.registerReferenceProvider(psiElement(JetForExpression.class),
                                            new PsiReferenceProvider() {
                                                @NotNull
                                                @Override
                                                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext processingContext) {
                                                    return toArray(new JetForLoopInReference((JetForExpression) element));
                                                }
                                            });
    }

    @NotNull
    private static JetReference[] toArray(@NotNull JetReference reference) {
        return new JetReference[] {reference};
    }
}
