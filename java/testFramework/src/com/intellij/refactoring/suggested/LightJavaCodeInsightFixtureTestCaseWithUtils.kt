// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class LightJavaCodeInsightFixtureTestCaseWithUtils : LightJavaCodeInsightFixtureTestCase() {
  protected fun deleteTextAtCaret(text: String) = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset, offset + text.length))
    require(actualText == text)
    editor.document.deleteString(offset, offset + text.length)
  }

  protected fun deleteTextBeforeCaret(text: String) = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset - text.length, offset))
    require(actualText == text)
    editor.document.deleteString(offset - text.length, offset)
  }

  protected fun replaceTextAtCaret(oldText: String, newText: String) = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset, offset + oldText.length))
    require(actualText == oldText)
    editor.document.replaceString(offset, offset + oldText.length, newText)
  }

  protected fun type(text: String) {
    myFixture.type(text)
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }

  protected fun performAction(actionId: String) {
    myFixture.performEditorAction(actionId)
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }

  protected fun editAction(action: () -> Unit) {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    executeCommand {
      runWriteAction {
        action()
        psiDocumentManager.commitAllDocuments()
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }
  }

  protected fun executeEditingActions(editingActions: () -> Unit) {
    editingActions()
    PsiDocumentManager.getInstance(this.project).commitAllDocuments()
  }
}