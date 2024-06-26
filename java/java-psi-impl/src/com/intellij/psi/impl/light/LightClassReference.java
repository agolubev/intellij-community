// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightClassReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  private final String myClassName;
  private final PsiElement myContext;
  private final @NotNull GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;
  private final PsiSubstitutor mySubstitutor;

  private LightClassReference(@NotNull PsiManager manager,
                              @NotNull @NonNls String text,
                              @Nullable @NonNls String className,
                              @Nullable PsiSubstitutor substitutor,
                              @NotNull GlobalSearchScope resolveScope,
                              @Nullable PsiElement context,
                              @Nullable PsiClass refClass) {
    super(manager, text);
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = context;
    myRefClass = refClass;
    mySubstitutor = substitutor;
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull @NonNls String className, @NotNull GlobalSearchScope resolveScope) {
    this(manager, text, className, null, resolveScope, null, null);
  }

  public LightClassReference(@NotNull PsiManager manager,
                             @NotNull @NonNls String text,
                             @NotNull @NonNls String className,
                             PsiSubstitutor substitutor,
                             @NotNull PsiElement context) {
    this(manager, text, className, substitutor, context.getResolveScope(), context, null);
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull PsiClass refClass) {
    this(manager, text, refClass, null);
  }

  public LightClassReference(@NotNull PsiManager manager, @NotNull @NonNls String text, @NotNull PsiClass refClass, PsiSubstitutor substitutor) {
    this(manager, text, null, substitutor, refClass.getResolveScope(), null, refClass);
  }

  @Override
  public PsiElement resolve() {
    if (myClassName != null) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      if (myContext != null) {
        return facade.getResolveHelper().resolveReferencedClass(myClassName, myContext);
      }
      else {
        return facade.findClass(myClassName, myResolveScope);
      }
    }
    else {
      return myRefClass;
    }
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode){
    final PsiElement resolved = resolve();
    if (resolved == null) {
      return JavaResolveResult.EMPTY;
    }
    PsiSubstitutor substitutor = mySubstitutor;
    if (substitutor == null) {
      if (resolved instanceof PsiClass) {
        substitutor = JavaPsiFacade.getElementFactory(myManager.getProject()).createRawSubstitutor((PsiClass) resolved);
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return new CandidateInfo(resolved, substitutor);
  }

  @Override
  public String getQualifiedName() {
    if (myClassName != null) {
      if (myContext != null) {
        PsiClass psiClass = (PsiClass)resolve();
        if (psiClass != null) {
          return psiClass.getQualifiedName();
        }
      }
      return myClassName;
    }
    return myRefClass.getQualifiedName();
  }

  @Override
  public String getReferenceName() {
    if (myClassName != null){
      return PsiNameHelper.getShortClassName(myClassName);
    }
    else{
      if (myRefClass instanceof PsiAnonymousClass){
        return ((PsiAnonymousClass)myRefClass).getBaseClassReference().getReferenceName();
      }
      else{
        return myRefClass.getName();
      }
    }
  }

  @Override
  public PsiElement copy() {
    if (myClassName != null) {
      if (myContext != null) {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myContext);
      }
      else{
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myResolveScope, null, null);
      }
    }
    else {
      return new LightClassReference(myManager, myText, myRefClass, mySubstitutor);
    }
  }

  @Override
  public boolean isValid() {
    return myRefClass == null || myRefClass.isValid();
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }
}
