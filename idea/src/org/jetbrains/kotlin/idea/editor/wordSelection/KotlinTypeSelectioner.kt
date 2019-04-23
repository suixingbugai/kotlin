/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinTypeSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean {
        return e is KtTypeReference
                && e.getStrictParentOfType<KtObjectDeclaration>() == null
                && e.getStrictParentOfType<KtParameter>() == null
    }

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        if (e !is KtTypeReference) return null
        val callableDeclaration = (e as KtTypeReference).parent as? KtCallableDeclaration ?: return null
        if (e != callableDeclaration.typeReference) return null
        val colonPosition = callableDeclaration.colon?.startOffset ?: return null
        return listOf(TextRange(colonPosition, e.endOffset))
    }
}
