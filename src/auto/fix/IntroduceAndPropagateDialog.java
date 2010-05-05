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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: Call me Ismail
 * Date: Apr 28, 2010
 * Time: 7:00:18 PM
 */
public class IntroduceAndPropagateDialog extends JDialog implements ActionListener
{
    private ButtonGroup scopeSelection;
    private PropagateSettingsIF propagateSettingsListener;
    private Project myProject;
    private PsiExpression myPsiExpression;
    private ProblemDescriptor myDescriptor;
    public static final String CLASS_ACTION_COMMAND = "classActionCommand";
    public static final String CLASS_HIERARCHY_COMMAND = "classHierarchyCommand";
    public static final String PACKAGE_ACTION_COMMAND = "packageActionCommand";
    private static final String OK_BUTTON_ACTION_COMMAND = "okButton";
    private JTextField constantName;
    private static final String PREVIEW_ACTION_COMMAND = "previewActionCommand";
    private static final String CANCEL_ACTION_COMMAND = "cancelActionCommand";

    public IntroduceAndPropagateDialog(Project project, PsiLiteralExpression psiExpression, ProblemDescriptor descriptor)
    {
        myProject = project;
        myPsiExpression = psiExpression;
        myDescriptor = descriptor;
        ensureEventDispatchThread();
        createDialog();
    }

    private void createDialog()
    {
        JPanel mainPanel = new JPanel(new BorderLayout());

        constantName = new JTextField(IntroduceAndPropagateConstantHandler.extractDefaultFieldName(myPsiExpression), 30);
        JLabel constantNameLabel = new JLabel("Name for new field constant:");
        JPanel constantNamePanel = new JPanel(new GridLayout(2,1,5,5));
        constantNamePanel.add(constantNameLabel);
        constantNamePanel.add(constantName);
        mainPanel.add(constantNamePanel, BorderLayout.NORTH);

        scopeSelection = new ButtonGroup();

        JRadioButton classScope = new JRadioButton("Class ["+ myPsiExpression.getContainingFile().getName() +"]");
        classScope.setActionCommand(CLASS_ACTION_COMMAND);
        classScope.setSelected(true);

        JRadioButton classHierarchyScope = new JRadioButton("Class Hierarchy [Base class: "+IntroduceAndPropagateConstantHandler.findBaseClass(
            PsiUtil.getTopLevelClass(myPsiExpression)).getName()+"]");
        classHierarchyScope.setActionCommand(CLASS_HIERARCHY_COMMAND);

        JRadioButton packageScope = new JRadioButton("Package [" + ((PsiJavaFile)myPsiExpression.getContainingFile()).getPackageName()+ "]");
        packageScope.setActionCommand(PACKAGE_ACTION_COMMAND);

        scopeSelection.add(classScope);
        scopeSelection.add(classHierarchyScope);
        scopeSelection.add(packageScope);

        JPanel radioPanel = new JPanel(new GridLayout(0,1));
        radioPanel.add(classScope);
        radioPanel.add(classHierarchyScope);
        radioPanel.add(packageScope);

        mainPanel.add(radioPanel,BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.setActionCommand(OK_BUTTON_ACTION_COMMAND);
        okButton.addActionListener(this);
        JButton previewButton = new JButton("Preview");
        previewButton.setActionCommand(PREVIEW_ACTION_COMMAND);
        previewButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand(CANCEL_ACTION_COMMAND);
        cancelButton.addActionListener(this);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(okButton);
        buttonPanel.add(previewButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setLayout(new GridLayout(1,1,10,10));
        add(mainPanel);

        pack();
    }

    private static void ensureEventDispatchThread()
    {
        if (!EventQueue.isDispatchThread())
        {
            throw new IllegalStateException("The IntroduceAndPropagateDialog can be used only on event dispatch thread.");
        }
    }

    public void actionPerformed(ActionEvent e)
    {
        String command = e.getActionCommand();
        if(!CANCEL_ACTION_COMMAND.equals(command))
        {
            propagateSettingsListener.setPropagateSettings(scopeSelection.getSelection().getActionCommand(), constantName.getText(), PREVIEW_ACTION_COMMAND.equals(command));
        }

        dispose();

    }

    public void addPropagateSettingsListener(PropagateSettingsIF settingsListener)
    {
        propagateSettingsListener = settingsListener;
    }

    @Override
    public void dispose()
    {
        propagateSettingsListener = null;
        super.dispose(); 
    }
}
