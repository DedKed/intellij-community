/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

/**
 * @author peter
 */
public abstract class BaseCodeCompletionAction extends DumbAwareAction implements HintManagerImpl.ActionToIgnore {

  protected BaseCodeCompletionAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  protected void invokeCompletion(AnActionEvent e, CompletionType type, int time) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    assert editor != null;
    Project project = editor.getProject();
    assert project != null;
    InputEvent inputEvent = e.getInputEvent();
    createHandler(type, true, false, true).invokeCompletion(project, editor, time, inputEvent != null && inputEvent.getModifiers() != 0, false);
  }

  @NotNull
  public CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {

    return new CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    e.getPresentation().setEnabled(false);

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    Project project = editor.getProject();
    PsiFile psiFile = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing() && !ApplicationManager.getApplication().isOnAir()) return;
    e.getPresentation().setEnabled(true);
  }
}
