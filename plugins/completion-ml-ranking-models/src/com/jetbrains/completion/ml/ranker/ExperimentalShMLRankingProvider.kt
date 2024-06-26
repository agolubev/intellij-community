// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language

class ExperimentalShMLRankingProvider : CatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.sh"), "sh_features_exp", "sh_model_exp"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("shell script", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}
