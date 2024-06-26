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
package com.intellij.usages;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Maxim.Mossienko
 */
public class PsiNamedElementUsageGroupBase<T extends PsiNamedElement & NavigationItem> extends PsiElementUsageGroupBase<T> {

  public PsiNamedElementUsageGroupBase(@NotNull T element, Icon icon, @NotNull String name) {
    super(element, icon, name);
  }

  public PsiNamedElementUsageGroupBase(@NotNull T element, Icon icon) {
    super(element, icon);
  }

  public PsiNamedElementUsageGroupBase(@NotNull T element, @NotNull String name) {
    super(element, name);
  }

  public PsiNamedElementUsageGroupBase(@NotNull T element) {
    super(element);
  }
}
