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
package auto.inspection;

import auto.fix.ConstantsExtractorFix;
import auto.fix.IntroduceAndPropagateDialog;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: Call me Ismail
 * Date: Apr 21, 2010
 * Time: 8:26:36 PM
 */
public class ConstantsExtractionInspection extends BaseJavaLocalInspectionTool
{
    public static boolean constExtract_autoEnabled = false;
    public static boolean constExtract_autoFix = false;
    public static boolean classActionCommand = true;
    public static boolean classHierarchyActionCommand = false;
    public static boolean packageActionCommand = false;
    private static boolean detected = false;
    private static PsiClass lastDetectedClass = null;

    @Override
    public JComponent createOptionsPanel()
    {
        return new ConstantsExtractionOptionsPanel(this, "constExtract_autoEnabled", "constExtract_autoFix","classActionCommand","classHierarchyActionCommand","packageActionCommand");
    }

    private synchronized static void setDetected(PsiLiteralExpression expression)
    {
        detected = true;
        lastDetectedClass = PsiUtil.getTopLevelClass(expression);
    }

    private synchronized static boolean isDetected()
    {
        return detected;
    }

    private synchronized static void checkDetected(PsiLiteralExpression expression)
    {
        if(detected)
        {
            if(!lastDetectedClass.equals(PsiUtil.getTopLevelClass(expression)))
            {
                detected = false;
            }
        }
    }


    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName()
    {
        return "Constants Extraction";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName()
    {
        return "Constants Extraction Tool";
    }

    @NotNull
    @Override
    public String getShortName()
    {
        return "ConstantsExtraction";
    }

    @Override
    public boolean isEnabledByDefault()
    {
        return true;
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly)
    {
        return new ConstantsExpressionVisitor(holder, isOnTheFly);
    }

    private class ConstantsExpressionVisitor extends JavaElementVisitor
    {
        private ProblemsHolder problemsHolder;
        private boolean isOnTheFly;

        public ConstantsExpressionVisitor(ProblemsHolder holder, boolean onTheFly)
        {
            problemsHolder = holder;
            isOnTheFly = onTheFly;
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression)
        {
            visitExpression(expression);
        }

        @Override
        public void visitLiteralExpression(final PsiLiteralExpression expression)
        {
            if(!expressionIsEmptyString(expression) && !isParentSystemPrint(expression)
               && !isNullKeyword(expression) && !isLocalVarDeclaration(expression) && !isLabelOrQuery(expression))
            {
                final ConstantsExtractorFix constantsExtractorFix = new ConstantsExtractorFix(expression);

                checkDetected(expression);

                // check parent is not already a field
                if(PsiTreeUtil.getParentOfType(expression, PsiField.class) == null)
                {
                    if (constExtract_autoFix || (constExtract_autoEnabled && !isDetected()))
                    {
                        if(!constExtract_autoFix)
                            setDetected(expression);
                        applyFixNow(expression, new ConstantsExtractorFix(expression));
                    }
                    else
                    {
                        registerProblem(expression, constantsExtractorFix);
                    }
                }
            }
        }

        private void applyFixNow(final PsiLiteralExpression expression, final ConstantsExtractorFix fixNow)
        {
            final Project project = expression.getProject();
            ApplicationManager.getApplication().invokeLater(new Runnable()
            {
                public void run()
                {
                    if(constExtract_autoFix)
                    {
                        String command = IntroduceAndPropagateDialog.CLASS_ACTION_COMMAND;
                        if(classHierarchyActionCommand)
                        {
                            command = IntroduceAndPropagateDialog.CLASS_HIERARCHY_COMMAND;
                        }
                        else if(packageActionCommand)
                        {
                            command = IntroduceAndPropagateDialog.PACKAGE_ACTION_COMMAND;
                        }
                        fixNow.applyDefaultFix(project, command);
                        /*fixNow.applyFix(project, InspectionManagerEx.getInstance(project).createProblemDescriptor(expression.getOriginalElement(),
                                                                                                                    getDisplayName(),
                                                                                                                    fixNow,
                                                                                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                                                    isOnTheFly));*/
                    }
                    else
                    {
                        PsiFile containingFile = expression.getContainingFile();
                        RunInspectionIntention.rerunInspection(InspectionProjectProfileManager.getInstance(project).getInspectionProfile().getInspectionTool(getShortName(),containingFile),
                                                                   (InspectionManagerEx)InspectionManagerEx.getInstance(project),
                                                                   new AnalysisScope(containingFile),
                                                                   containingFile);
                    }
                }
            });
        }

        private void registerProblem(PsiLiteralExpression expression, ConstantsExtractorFix fixNow)
        {
            problemsHolder.registerProblem(expression, "Unassigned literal expression", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,fixNow);
        }

        @Override
        public void visitElement(PsiElement element)
        {
            if(element instanceof PsiLiteral)
            {
                problemsHolder.registerProblem(element, "Just a PsiLiteral . . .", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }
    }

    private static boolean isLabelOrQuery(PsiLiteralExpression expression)
    {
        boolean retVal = false;
        PsiElement firstChild = expression.getFirstChild();
        if(firstChild instanceof PsiJavaToken)
        {
            if(((PsiJavaToken)firstChild).getTokenType().toString().equals(JavaTokenType.STRING_LITERAL.toString()))
            {
                retVal = expression.getText().indexOf(" ") > -1;
                if(!retVal)
                {
                    PsiElement parent = expression.getParent();
                    if(parent != null)
                    {
                        boolean questFound = false;
                        boolean colonFound = false;
                        boolean plusFound = false;
                        for(PsiElement child : parent.getChildren())
                        {
                            if(child instanceof PsiJavaToken)
                            {
                                String tokenTypeString = ((PsiJavaToken)child).getTokenType().toString();
                                if(tokenTypeString.equals(JavaTokenType.PLUS.toString()))
                                    plusFound = true;
                                else
                                {
                                    if(tokenTypeString.equals(JavaTokenType.QUEST.toString()))
                                        questFound = true;
                                    else if(tokenTypeString.equals(JavaTokenType.COLON.toString()))
                                        colonFound = true;
                                }

                                if(plusFound || (questFound && colonFound))
                                {
                                    retVal= true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return retVal;
    }

    private static boolean isLocalVarDeclaration(PsiLiteralExpression expression)
    {
        boolean retVal = false;
        PsiElement localVarParent = expression.getParent();
        if(localVarParent != null && localVarParent instanceof PsiLocalVariable)
        {
            PsiElement declarationParent = localVarParent.getParent();
            if(declarationParent != null && declarationParent instanceof PsiDeclarationStatement)
            {
                retVal = true;
            }
        }
        return retVal;
    }

    private static boolean isNullKeyword(PsiLiteralExpression expression)
    {
        boolean retVal = false;
        PsiElement firstChild = expression.getFirstChild();
        if(firstChild != null && firstChild instanceof PsiJavaToken)
        {
            retVal = expression.getText().equals(PsiKeyword.NULL);
        }
        return retVal;
    }

    private static boolean isParentSystemPrint(PsiLiteralExpression expression)
    {
        PsiElement expressionParent = expression.getParent();
        while(expressionParent != null && !(expressionParent instanceof PsiMethodCallExpression))
        {
            expressionParent = expressionParent.getParent();
        }

        boolean retVal = false;

        if (expressionParent != null)
        {
            retVal = expressionParent.getText().matches("System.*print.*");
        }

        return retVal;
    }

    private static boolean expressionIsEmptyString(PsiLiteralExpression expression)
    {
        return expression.getText().equals("\"\"");
    }

}
