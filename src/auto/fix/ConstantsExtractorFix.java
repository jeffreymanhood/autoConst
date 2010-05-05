/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package auto.fix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * User: Call me Ismail
 * Date: Apr 21, 2010
 * Time: 8:43:20 PM
 */
public class ConstantsExtractorFix implements LocalQuickFix, EventListener
{
    private PsiLiteralExpression constantExpression;

    public ConstantsExtractorFix(PsiLiteralExpression expression)
    {
        constantExpression = expression;
    }

    @NotNull
    public String getName()
    {
        return "Constants Extractor Fix";
    }

    @NotNull
    public String getFamilyName()
    {
        return "Constants Extractor";
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
    {
        IntroduceAndPropagateConstantHandler introduceConstantHandler = new IntroduceAndPropagateConstantHandler(project, constantExpression);
        introduceConstantHandler.invoke(project, constantExpression, descriptor);
    }

    public void applyDefaultFix(Project project, String command)
    {
        IntroduceAndPropagateConstantHandler introduceConstantHandler = new IntroduceAndPropagateConstantHandler(project, constantExpression);
        introduceConstantHandler.setPropagateSettings(command, IntroduceAndPropagateConstantHandler.extractDefaultFieldName(constantExpression), false);
    }
}
