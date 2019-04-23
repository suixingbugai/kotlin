/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.combineWhenConditions
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.util.*


class CommaInWhenConditionWithoutArgumentFix(element: PsiElement) : KotlinQuickFixAction<PsiElement>(element), CleanupFix {
    override fun getFamilyName(): String = text
    override fun getText(): String = "Replace ',' with '||' in when"

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val whenExpression = element as? KtWhenExpression ?: return
        replaceCommasWithOrsInWhenExpression(whenExpression)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
                diagnostic.psiElement.parent?.parent?.let(::CommaInWhenConditionWithoutArgumentFix)

        private class WhenEntryConditionsData(
                val conditions: Array<KtWhenCondition>,
                val first: PsiElement,
                val last: PsiElement,
                val arrow: PsiElement
        )

        private fun replaceCommasWithOrsInWhenExpression(whenExpression: KtWhenExpression) {
            for (whenEntry in whenExpression.entries) {
                if (whenEntry.conditions.size > 1) {
                    val conditionsData = getConditionsDataOrNull(whenEntry) ?: return
                    val replacement = KtPsiFactory(whenEntry).combineWhenConditions(conditionsData.conditions, null) ?: return
                    whenEntry.deleteChildRange(conditionsData.first, conditionsData.last)
                    whenEntry.addBefore(replacement, conditionsData.arrow)
                }
            }
        }

        private fun getConditionsDataOrNull(whenEntry: KtWhenEntry): WhenEntryConditionsData? {
            val conditions = ArrayList<KtWhenCondition>()

            var arrow: PsiElement? = null

            var child = whenEntry.firstChild
            whenEntryChildren@ while (child != null) {
                when {
                    child is KtWhenConditionWithExpression -> {
                        conditions.add(child as KtWhenConditionWithExpression)
                    }
                    child.node.elementType == KtTokens.ARROW -> {
                        arrow = child
                        break@whenEntryChildren
                    }
                }
                child = child.nextSibling
            }

            val last = child?.prevSibling

            return if (arrow != null && last != null)
                WhenEntryConditionsData(conditions.toTypedArray(), whenEntry.firstChild, last!!, arrow!!)
            else
                null
        }

    }

}