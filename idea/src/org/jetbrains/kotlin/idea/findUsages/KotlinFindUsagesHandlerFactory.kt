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

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.CommonBundle
import com.intellij.find.FindBundle
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.OverridingMethodsSearch
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.findUsages.handlers.DelegatingFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinTypeParameterFindUsagesHandler
import org.jetbrains.kotlin.idea.refactoring.checkSuperMethods
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinFindUsagesHandlerFactory(project: Project) : FindUsagesHandlerFactory() {
    val javaHandlerFactory = JavaFindUsagesHandlerFactory(project)

    val findFunctionOptions: KotlinFunctionFindUsagesOptions = KotlinFunctionFindUsagesOptions(project)
    val findPropertyOptions = KotlinPropertyFindUsagesOptions(project)
    val findClassOptions = KotlinClassFindUsagesOptions(project)
    val defaultOptions = FindUsagesOptions(project)

    override fun canFindUsages(element: PsiElement): Boolean =
        element is KtClassOrObject ||
                element is KtNamedFunction ||
                element is KtProperty ||
                element is KtParameter ||
                element is KtTypeParameter ||
                element is KtConstructor<*>

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        when (element) {
            is KtClassOrObject ->
                return KotlinFindClassUsagesHandler(element as KtClassOrObject, this)

            is KtParameter -> {
                if (!forHighlightUsages) {
                    if ((element as KtParameter).hasValOrVar()) {
                        val declarationsToSearch = checkSuperMethods(element as KtParameter, null, "find usages of")
                        return handlerForMultiple(element as KtParameter, declarationsToSearch)
                    }
                    val function = (element as KtParameter).ownerFunction
                    if (function != null && function!!.isOverridable()) {
                        val psiMethod = function!!.toLightMethods().singleOrNull()
                        if (psiMethod != null) {
                            val hasOverridden = OverridingMethodsSearch.search(psiMethod!!).any()
                            if (hasOverridden && askWhetherShouldSearchForParameterInOverridingMethods(element as KtParameter)) {
                                val parametersCount = psiMethod!!.parameterList.parametersCount
                                val parameterIndex = element.parameterIndex()
                                assert(parameterIndex < parametersCount)
                                val overridingParameters = OverridingMethodsSearch.search(psiMethod!!, true)
                                    .filter { it.parameterList.parametersCount == parametersCount }
                                    .mapNotNull { it.parameterList.parameters[parameterIndex].unwrapped }
                                return handlerForMultiple(element as KtParameter, listOf(element as KtParameter) + overridingParameters)
                            }
                        }

                    }
                }

                return KotlinFindMemberUsagesHandler.getInstance(element as KtParameter, factory = this)
            }

            is KtNamedFunction, is KtProperty, is KtConstructor<*> -> {
                val declaration = element as KtNamedDeclaration

                if (forHighlightUsages) {
                    return KotlinFindMemberUsagesHandler.getInstance(declaration, factory = this)
                }

                val declarationsToSearch = checkSuperMethods(declaration, null, "find usages of")
                return handlerForMultiple(declaration, declarationsToSearch)
            }

            is KtTypeParameter ->
                return KotlinTypeParameterFindUsagesHandler(element as KtTypeParameter, this)

            else ->
                throw IllegalArgumentException("unexpected element type: $element")
        }
    }

    private fun handlerForMultiple(originalDeclaration: KtNamedDeclaration, declarations: Collection<PsiElement>): FindUsagesHandler {
        return when (declarations.size) {
            0 -> FindUsagesHandler.NULL_HANDLER

            1 -> {
                val target = declarations.single().unwrapped ?: return FindUsagesHandler.NULL_HANDLER
                if (target is KtNamedDeclaration) {
                    KotlinFindMemberUsagesHandler.getInstance(target as KtNamedDeclaration, factory = this)
                } else {
                    javaHandlerFactory.createFindUsagesHandler(target, false)!!
                }
            }

            else -> DelegatingFindMemberUsagesHandler(originalDeclaration, declarations, factory = this)
        }
    }

    private fun askWhetherShouldSearchForParameterInOverridingMethods(parameter: KtParameter): Boolean {
        return Messages.showOkCancelDialog(
            parameter.project,
            FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.name),
            FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
            CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
            Messages.getQuestionIcon()
        ) == Messages.OK
    }
}
