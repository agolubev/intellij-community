/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.rename;

import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.util.FileTypeUtils;

public final class JavaVetoRenameCondition implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    if (element instanceof LightMethod) {
      PsiClass containingClass = ((LightMethod)element).getContainingClass();
      if (containingClass.isEnum()) return true;
    }

    if (element instanceof PsiReceiverParameter) {
      return true;
    }

    return element instanceof PsiJavaFile &&
           !FileTypeUtils.isInServerPageFile(element) &&
           !JavaProjectRootsUtil.isOutsideJavaSourceRoot((PsiFile)element) &&
           ((PsiJavaFile) element).getClasses().length > 0;
  }
}
