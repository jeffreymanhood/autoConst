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
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
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

    public void applyDefaultFix(Project project, String command, boolean useSuggestedName)
    {
        IntroduceAndPropagateConstantHandler introduceConstantHandler = new IntroduceAndPropagateConstantHandler(project, constantExpression);
        if(useSuggestedName)
        {
            introduceConstantHandler.setPropagateSettings(command, IntroduceAndPropagateConstantHandler.extractDefaultFieldName(constantExpression), false);
        }
        else
        {
            //TODO: there's some utility that let's you highlight the actual text in the file and rename right in the editor - figure out how to use that 
            String suggestedName = IntroduceAndPropagateConstantHandler.extractDefaultFieldName(constantExpression);
            String constantName = Messages
                .showEditableChooseDialog("Set constant name","Rename Suggested Constant Name", null, new String[]{}, suggestedName, new InputValidator()
                    {
                        public boolean checkInput(String inputString)
                        {
                            return true;
                        }

                        public boolean canClose(String inputString)
                        {
                            return true;
                        }
                    });
            introduceConstantHandler.setPropagateSettings(command, constantName != null ? constantName : suggestedName, false);
        }
    }
}
