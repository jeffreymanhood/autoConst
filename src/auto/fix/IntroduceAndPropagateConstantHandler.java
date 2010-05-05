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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Query;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: Call me Ismail
 * Date: Apr 28, 2010
 * Time: 6:29:58 PM
 */
public class IntroduceAndPropagateConstantHandler extends BaseRefactoringProcessor implements PropagateSettingsIF
{
    private Project myProject;
    private PsiLiteralExpression myLiteralExpression;
    private String lastActionCommand = IntroduceAndPropagateDialog.CLASS_ACTION_COMMAND;
    private static final String REFACTORING_NAME = "IntroduceAndPropagateConstant";
    private String lastConstantName;

    public IntroduceAndPropagateConstantHandler(Project project, PsiLiteralExpression literalExpression)
    {
        super(project);
        myProject = project;
        myLiteralExpression = literalExpression;
    }


    public void invoke(Project project, PsiLiteralExpression psiExpression, ProblemDescriptor descriptor)
    {
        IntroduceAndPropagateDialog dialog = new IntroduceAndPropagateDialog(project, psiExpression, descriptor);
        dialog.addPropagateSettingsListener(this);
        dialog.setVisible(true);        
    }

    public void setPropagateSettings(String actionCommand, String constantName, boolean isPreview)
    {
        lastActionCommand = actionCommand;
        lastConstantName = constantName;

        setPreviewUsages(isPreview);

        super.doRun();
    }

    private void processClassActionCommand(String constantName)
    {
        try
        {
            HashSet<PsiExpression> psiExpressions = findClassActionOccurrences(PsiUtil.getTopLevelClass(myLiteralExpression));
            createConstants(psiExpressions, PsiUtil.getTopLevelClass(myLiteralExpression),
                        PsiModifier.PRIVATE, false, constantName);
        }
        catch(PsiInvalidElementAccessException psi)
        {
            //ignore - this means that the occurrence of the expression no longer exists
            //that being the case, there's no need to carry out the refactoring
        }
    }

    private HashSet<PsiExpression> findClassActionOccurrences(PsiClass searchClass)
    {
        PsiExpression[] occurrences = new PsiExpression[0];
        try
        {
            ExpressionOccurenceManager occurrenceManager = new ExpressionOccurenceManager(myLiteralExpression, searchClass, null);

            occurrences = occurrenceManager.getOccurences();
        }
        catch(PsiInvalidElementAccessException psi)
        {
            //ignore - this means that the occurrence of the expression no longer exists
        }

        return cleanseFieldOccurrences(new HashSet<PsiExpression>(Arrays.asList(occurrences)));
    }

    private static HashSet<PsiExpression> cleanseFieldOccurrences(HashSet<PsiExpression> psiExpressions)
    {
        HashSet<PsiExpression> removeTheseExpression = new HashSet<PsiExpression>();
        for(PsiExpression expression : psiExpressions)
        {
            if(expression instanceof PsiField)
            {
                removeTheseExpression.add(expression);
            }
        }

        psiExpressions.removeAll(removeTheseExpression);

        return psiExpressions;
    }

    private void processPackageActionCommand(String constantName)
    {
        PsiPackage aPackage = retrievePackage();

        HashSet<PsiExpression> elementsFound = findPackageActionOccurrences(aPackage);

        if(elementsFound.size() > 0)
        {
            PsiClass newInterface = createNewInterface(aPackage);
            createConstants(elementsFound, newInterface, PsiModifier.PACKAGE_LOCAL, true, constantName);
        }
    }

    private HashSet<PsiExpression> findPackageActionOccurrences(PsiPackage aPackage)
    {
        HashSet<PsiExpression> elementsFound = new HashSet<PsiExpression>();
        if(aPackage != null)
        {
            searchPackagesForOccurrences(aPackage, elementsFound);
            for(PsiPackage subPackage : aPackage.getSubPackages())
            {
                searchPackagesForOccurrences(subPackage, elementsFound);
            }
        }
        return elementsFound;
    }

    private PsiPackage retrievePackage()
    {
        PsiPackage aPackage = null;
        try
        {
            String packageName = ((PsiJavaFile)myLiteralExpression.getContainingFile()).getPackageName();
            aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
        }
        catch(PsiInvalidElementAccessException psi)
        {
            // ignore - this is caused by auto mode processing elements that have already been replaced
        }
        return aPackage;
    }

    private void searchPackagesForOccurrences(PsiPackage aPackage, HashSet<PsiExpression> elementsFound)
    {
        if (aPackage != null)
        {
            GlobalSearchScope searchScope = PackageScope.packageScope(aPackage, true);
            Query<PsiClass> query = AllClassesSearch.search(searchScope, myProject);
            getOccurrencesFromFindAllQuery(query, elementsFound);
        }
    }

    private static PsiClass createNewInterface(final PsiPackage aPackage)
    {
        final PsiClass[] targetClass = new PsiClass[1];

        Runnable action = new Runnable()
        {
            public void run()
            {
                PsiDirectory directory = aPackage.getDirectories()[0];
                String fileName = createConstantsIFName(aPackage.getName()) + "ConstantsIF";

                String javaFileName = fileName + ".java";
                if(directory.findFile(javaFileName) == null)
                {
                    targetClass[0] = JavaDirectoryService.getInstance()
                        .createInterface(directory, fileName);
                    PsiUtil.setModifierProperty(targetClass[0], PsiKeyword.PUBLIC, true);
                }
                else
                {
                    for(PsiClass psiClass : JavaDirectoryService.getInstance().getClasses(directory))
                    {
                        if(psiClass.getName().equals(fileName))
                        {
                            targetClass[0] = psiClass;
                            break;
                        }
                    }
                }
            }
        };

        createAndRunCommand(aPackage.getProject(), action);

        return targetClass[0];
    }

    private static void createAndRunCommand(Project project, final Runnable action)
    {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runWriteAction(action);
            }
        }, REFACTORING_NAME, null);
    }

    private static String createConstantsIFName(String name)
    {
        String retVal = name;
        int lastIndexOf= name.lastIndexOf(".");
        if(lastIndexOf > 0)
        {
            retVal = name.substring(lastIndexOf+1, name.length());
        }

        String firstInitial = retVal.substring(0,1);
        firstInitial = firstInitial.toUpperCase();
        retVal = firstInitial + retVal.substring(1,retVal.length());

        return retVal;
    }

    private void processClassHierarchyActionCommand(String constantName)
    {
        PsiClass topLevelClass = PsiUtil.getTopLevelClass(myLiteralExpression);
        PsiClass baseClass = findBaseClass(topLevelClass);

        HashSet<PsiExpression> tempElementsFound = findClassHierarchyActionOccurrences(baseClass);

        createConstants(tempElementsFound, baseClass, PsiModifier.PROTECTED, false, constantName);
    }

    private HashSet<PsiExpression> findClassHierarchyActionOccurrences(PsiClass baseClass)
    {
        @SuppressWarnings({"ConstantConditions"})
        SearchScope scope = GlobalSearchScope.moduleScope(ModuleUtil.findModuleForPsiElement(myLiteralExpression.getContainingFile()));
        Query<PsiClass> query = ClassInheritorsSearch.search(baseClass, scope, true, true);

        HashSet<PsiExpression> elementsFound = new HashSet<PsiExpression>();
        getOccurrencesFromFindAllQuery(query, elementsFound);

        elementsFound.addAll(findClassActionOccurrences(baseClass));

        return elementsFound;
    }

    private void getOccurrencesFromFindAllQuery(Query<PsiClass> query, HashSet<PsiExpression> elementsFound)
    {
        for(PsiClass psiClass : query.findAll())
        {
            ExpressionOccurenceManager occurrenceManager = new ExpressionOccurenceManager(myLiteralExpression, psiClass, null);
            PsiExpression[] occurrences = occurrenceManager.getOccurences();
            elementsFound.addAll(Arrays.asList(occurrences));
        }
    }

    static PsiClass findBaseClass(PsiClass topLevelClass)
    {
        PsiClass retVal = topLevelClass;

        PsiReferenceList extendsList = topLevelClass.getExtendsList();
        if(extendsList != null && extendsList.getChildren().length > 0 && topLevelClass.getSuperClass() != null)
        {
            retVal = findBaseClass(topLevelClass.getSuperClass());
        }

        return retVal;
    }

    private static void createConstants(HashSet<PsiExpression> psiExpressions, PsiClass destinationClass, @Subst("") String visibility,
                                        boolean isDestinationClassNew, String constantName)
    {
        PsiManager psiManager = destinationClass.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
        @SuppressWarnings({"ConstantConditions"})
        PsiField field = factory.createField(constantName, psiExpressions.iterator().next().getType());
        field.setInitializer(psiExpressions.iterator().next());
        //noinspection ConstantConditions
        field.getModifierList().setModifierProperty(visibility, true);
        if(anyStaticOccurrences(psiExpressions))
        {
            //noinspection ConstantConditions
            field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        }

        PsiElement multiExpressionsAnchor =
            RefactoringUtil.getAnchorElementForMultipleExpressions(psiExpressions.toArray(new PsiExpression[psiExpressions.size()]), null);

        if(multiExpressionsAnchor != null && destinationClass.equals(PsiUtil.getTopLevelClass(multiExpressionsAnchor)))
        {
            while(!multiExpressionsAnchor.getParent().equals(destinationClass))
            {
                multiExpressionsAnchor = multiExpressionsAnchor.getParent();
            }

            if(destinationClass.getFields().length > 0)
            {
                PsiField[] fields = destinationClass.getFields();
                multiExpressionsAnchor = fields[fields.length - 1];
            }
        }

        field = (PsiField)CodeStyleManager.getInstance(psiManager.getProject()).reformat(field);
        writeFieldToDestinationClass(destinationClass, field, multiExpressionsAnchor, psiExpressions, isDestinationClassNew, createExpressionsClassMap(psiExpressions));

    }

    private static HashMap<PsiExpression, PsiJavaFile> createExpressionsClassMap(HashSet<PsiExpression> psiExpressions)
    {
        HashMap<PsiExpression, PsiJavaFile> retVal = new HashMap<PsiExpression, PsiJavaFile>();
        for(PsiExpression expression : psiExpressions)
        {
            retVal.put(expression, (PsiJavaFile)expression.getContainingFile());
        }
        return retVal;
    }

    private static boolean anyStaticOccurrences(HashSet<PsiExpression> psiExpressions)
    {
        boolean retVal = false;

        Iterator<PsiExpression> iterator = psiExpressions.iterator();
        while(iterator.hasNext() && !retVal)
        {
            PsiMethod method = findContainingMethod(iterator.next());
            retVal = method.hasModifierProperty(PsiModifier.STATIC);
        }

        return retVal;
    }

    private static PsiMethod findContainingMethod(PsiExpression expression)
    {
        PsiElement methodElement= expression.getParent();
        while(!(methodElement instanceof PsiMethod || methodElement instanceof PsiClass))
        {
            methodElement = methodElement.getParent();
        }

        assert methodElement instanceof PsiMethod;

        return (PsiMethod)methodElement;
    }

    private static void writeFieldToDestinationClass(final PsiClass destinationClass, final PsiField field, final PsiElement multiExpressionsAnchor,
                                                     final HashSet<PsiExpression> psiExpressions,
                                                     final boolean isDestinationClassNew,
                                                     final HashMap<PsiExpression, PsiJavaFile> expressionsClassMap)
    {
        final PsiJavaFile destinationFile = (PsiJavaFile)destinationClass.getContainingFile();

        Runnable action = new Runnable()
        {

            public void run()
            {
                destinationClass.add(field);
                Project project = field.getManager().getProject();

                if (multiExpressionsAnchor != null && multiExpressionsAnchor instanceof PsiField)
                {
                    destinationClass.addAfter(CodeEditUtil.createLineFeed(field.getManager()), multiExpressionsAnchor);
                    CodeStyleManager.getInstance(project).adjustLineIndent(destinationClass.getContainingFile(), field.getTextRange());
                }

                for (PsiExpression occurrence : psiExpressions)
                {
                    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(project);
                    PsiElementFactory factory = javaFacade.getElementFactory();
                    IntroduceVariableBase.replace(occurrence, factory.createExpressionFromText(
                        isDestinationClassNew ? destinationClass.getName() + "." + field.getName() : field.getName(), field.getContext()),
                                                  project);

                    PsiJavaFile occurrenceJavaFile = expressionsClassMap.get(occurrence);
                    if(!destinationFile.getPackageName().equals(occurrenceJavaFile.getPackageName()))
                    {
                        //noinspection ConstantConditions
                        occurrenceJavaFile.getImportList().add(JavaPsiFacade.getInstance(project).getElementFactory().createImportStatement(destinationClass));
                    }
                }
            }
        };
        
        createAndRunCommand(field.getProject(), action);
    }

    static String extractDefaultFieldName(PsiExpression psiExpression)
    {
        String retVal = "DEFAULT_FIELD_NAME";

        if(psiExpression != null)
        {
            try
            {
                PsiType type = psiExpression.getType();
                if(type != PsiType.DOUBLE && type != PsiType.BYTE && type != PsiType.FLOAT && type != PsiType.INT && type != PsiType.LONG && type != PsiType.SHORT)
                {
                    retVal = psiExpression.getText();
                    retVal = retVal.replaceAll("[\\\"\'.]","");
                    retVal = retVal.replaceAll(" ","_");
                    retVal = retVal.trim();
                    retVal = retVal.toUpperCase();
                }
                else
                {
                    //noinspection ConstantConditions
                    retVal = psiExpression.getType().getPresentableText().toUpperCase()+"_"+psiExpression.getText();
                }
            }
            catch(NullPointerException np)
            {
                //ignore - this is a result of the expression no longer having a reference to the file
                //this is caught again later on and results in no refactoring being performed
            }
        }

        return retVal;
    }

    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages)
    {
        HashSet<PsiExpression> hashSet = findOccurrences();
        return new IntroduceAndPropagateConstantViewDescriptor(hashSet);
    }

    private HashSet<PsiExpression> findOccurrences()
    {
        HashSet<PsiExpression> hashSet = findClassActionOccurrences(PsiUtil.getTopLevelClass(myLiteralExpression));
        if(lastActionCommand.equals(IntroduceAndPropagateDialog.CLASS_HIERARCHY_COMMAND))
        {
            hashSet = findClassHierarchyActionOccurrences(findBaseClass(PsiUtil.getTopLevelClass(myLiteralExpression)));
        }
        else if(lastActionCommand.equals(IntroduceAndPropagateDialog.PACKAGE_ACTION_COMMAND))
        {
            hashSet = findPackageActionOccurrences(retrievePackage());
        }
        return hashSet;
    }

    @NotNull
    @Override
    protected UsageInfo[] findUsages()
    {
        HashSet<PsiExpression> occurrences;
        occurrences = findOccurrences();

        UsageInfo[] retVal = new UsageInfo[occurrences.size()];
        ArrayList<UsageInfo> temp = new ArrayList<UsageInfo>();

        for(PsiExpression expression : occurrences)
        {
            temp.add(new UsageInfo(expression));
        }

        retVal = temp.toArray(retVal);

        return retVal;
    }

    @Override
    protected void refreshElements(PsiElement[] elements)
    {
        //TODO: what is this?
        PsiElement element = elements[0];
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void performRefactoring(UsageInfo[] usages)
    {
        if(lastActionCommand.equals(IntroduceAndPropagateDialog.CLASS_HIERARCHY_COMMAND))
        {
            processClassHierarchyActionCommand(lastConstantName);
        }
        else if (lastActionCommand.equals(IntroduceAndPropagateDialog.CLASS_ACTION_COMMAND))
        {
            processClassActionCommand(lastConstantName);
        }
        else if (lastActionCommand.equals(IntroduceAndPropagateDialog.PACKAGE_ACTION_COMMAND))
        {
            processPackageActionCommand(lastConstantName);
        }
        else
        {
            Messages.showErrorDialog("Action setting "+lastActionCommand+" not found", "Missing action command");
        }
    }

    @Override
    protected String getCommandName()
    {
        return REFACTORING_NAME;
    }
}
