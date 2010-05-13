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

import auto.fix.IntroduceAndPropagateDialog;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.ui.VerticalFlowLayout;
import sun.reflect.Reflection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.lang.reflect.Field;

public class ConstantsExtractionOptionsPanel extends JPanel
{
    private InspectionProfileEntry owner;
    private String autoExtractProperty;
    private String autoFixProperty;
    private String classActionCommand;
    private String classHierarchyActionCommand;
    private String packageActionCommand;

    public ConstantsExtractionOptionsPanel(InspectionProfileEntry constantsExtractionInspection, String autoExtractionProperty, String autoFixProperty,
                                           String classActionCommand,
                                           String classHierarchyActionCommand,
                                           String packageActionCommand)
    {
        owner = constantsExtractionInspection;
        autoExtractProperty = autoExtractionProperty;
        this.autoFixProperty = autoFixProperty;
        this.classActionCommand = classActionCommand;
        this.classHierarchyActionCommand = classHierarchyActionCommand;
        this.packageActionCommand = packageActionCommand;
        createOptionsPanel();
    }

    public void createOptionsPanel()
    {
        setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 1, 1, true, false));

        // SETUP THE BUTTON GROUP FOR SCOPE SELECTION
        JLabel scopeLabel = new JLabelNoGuiUtils("Select Scope for Applying Auto Fix");

        final JRadioButton classScope = new JRadioButtonNoGuiUtils("Class");
        classScope.setActionCommand(IntroduceAndPropagateDialog.CLASS_ACTION_COMMAND);
        classScope.setSelected(getPropertyValue(owner, classActionCommand));

        final JRadioButton classHierarchyScope = new JRadioButtonNoGuiUtils("Class Hierarchy");
        classHierarchyScope.setActionCommand(IntroduceAndPropagateDialog.CLASS_HIERARCHY_COMMAND);
        classHierarchyScope.setSelected(getPropertyValue(owner, classHierarchyActionCommand));

        final JRadioButton packageScope = new JRadioButtonNoGuiUtils("Package");
        packageScope.setActionCommand(IntroduceAndPropagateDialog.PACKAGE_ACTION_COMMAND);
        packageScope.setSelected(getPropertyValue(owner, packageActionCommand));

        final ButtonGroup scopeSelection = new ButtonGroup();

        scopeSelection.add(classScope);
        scopeSelection.add(classHierarchyScope);
        scopeSelection.add(packageScope);

        classScope.getModel().addChangeListener(new ChangeListener(){public void stateChanged(ChangeEvent e){setCommandPropertyValues(scopeSelection);}});
        classHierarchyScope.getModel().addChangeListener(new ChangeListener(){public void stateChanged(ChangeEvent e){setCommandPropertyValues(scopeSelection);}});
        packageScope.getModel().addChangeListener(new ChangeListener(){public void stateChanged(ChangeEvent e){setCommandPropertyValues(scopeSelection);}});

        // SET UP THE DEFAULT CHECK BOXES
        JCheckBox autoExtractionModeCheckBox = new JCheckBox("Enable Auto Mode for Constants Extraction", getPropertyValue(owner, autoExtractProperty));
        final JCheckBox autoFixModeCheckBox = new JCheckBox("Enable Auto Mode for Constants Propagation", getPropertyValue(owner, autoFixProperty))
        {
            @Override
            public void setEnabled(boolean b)
            {
                // best hack ever - don't touch my components GuiUtils! You and your enableChildren method ... don't bring that here.
                if(!Reflection.getCallerClass(3).toString().matches(".*GuiUtils.*"))
                {
                    super.setEnabled(b);
                }
            }
        };

        final ButtonModel autoFixModel = autoFixModeCheckBox.getModel();
        autoFixModel.addChangeListener(new ChangeListener()
        {
            public void stateChanged(ChangeEvent e)
            {
                boolean autoFixSelected = autoFixModel.isSelected();
                setPropertyValue(owner, autoFixProperty, autoFixSelected);
                classScope.setEnabled(autoFixSelected);
                classHierarchyScope.setEnabled(autoFixSelected);
                packageScope.setEnabled(autoFixSelected);
                if(autoFixSelected)
                {
                    setCommandPropertyValues(scopeSelection);
                }
            }
        });

        final ButtonModel autoModeModel = autoExtractionModeCheckBox.getModel();
        autoModeModel.addChangeListener(new ChangeListener()
        {
            public void stateChanged(ChangeEvent e)
            {
                boolean autoModeSelected = autoModeModel.isSelected();
                setPropertyValue(owner, autoExtractProperty, autoModeSelected);
                autoFixModeCheckBox.setEnabled(autoModeSelected);
                if(!autoModeSelected)
                {
                    autoFixModeCheckBox.setSelected(autoModeSelected);
                }
            }
        });


        // ADD EVERYTHING TO THE PANEL
        add(autoExtractionModeCheckBox);

        JPanel tabbedAutoFixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabbedAutoFixPanel.add(new JLabel("    "));
        tabbedAutoFixPanel.add(autoFixModeCheckBox);
        add(tabbedAutoFixPanel);

        add(createDoubleTabbedPanel(scopeLabel));
        add(createDoubleTabbedPanel(classScope));
        add(createDoubleTabbedPanel(classHierarchyScope));
        add(createDoubleTabbedPanel(packageScope));

        autoFixModeCheckBox.setEnabled(autoModeModel.isSelected());
        classScope.setEnabled(autoFixModel.isSelected());
        classHierarchyScope.setEnabled(autoFixModel.isSelected());
        packageScope.setEnabled(autoFixModel.isSelected());
    }

    void setCommandPropertyValues(ButtonGroup scopeSelection)
    {
        String selectionCommand = scopeSelection.getSelection().getActionCommand();
        setPropertyValue(owner, classActionCommand, selectionCommand.equals(IntroduceAndPropagateDialog.CLASS_ACTION_COMMAND));
        setPropertyValue(owner, classHierarchyActionCommand, selectionCommand.equals(IntroduceAndPropagateDialog.CLASS_HIERARCHY_COMMAND));
        setPropertyValue(owner, packageActionCommand, selectionCommand.equals(IntroduceAndPropagateDialog.PACKAGE_ACTION_COMMAND));
    }

    private static JPanel createDoubleTabbedPanel(Component component)
    {
        JPanel retVal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        retVal.add(new JLabel("        "));
        retVal.add(component);
        return retVal;
    }

    private static boolean getPropertyValue(InspectionProfileEntry owner, String property)
    {
        try
        {
            final Class<? extends InspectionProfileEntry> aClass = owner.getClass();
            final Field field = aClass.getField(property);
            return field.getBoolean(owner);
        }
        catch (IllegalAccessException ignore)
        {
            return false;
        }
        catch (NoSuchFieldException ignore)
        {
            return false;
        }

    }

    private static void setPropertyValue(InspectionProfileEntry owner, String property, boolean selected)
    {
        try
        {
            final Class<? extends InspectionProfileEntry> aClass = owner.getClass();
            final Field field = aClass.getField(property);
            field.setBoolean(owner, selected);
        }
        catch (IllegalAccessException ignore)
        {
            // do nothing
        }
        catch (NoSuchFieldException ignore)
        {
            // do nothing
        }
    }


    private static class JLabelNoGuiUtils extends JLabel
    {
        public JLabelNoGuiUtils(String s)
        {
            super(s);
        }

        @Override
        public void setEnabled(boolean enabled)
        {
            // the hack so good we had to use it twice
            if(!Reflection.getCallerClass(3).toString().matches(".*GuiUtils.*"))
            {
                super.setEnabled(enabled);
            }
        }
    }

    private static class JRadioButtonNoGuiUtils extends JRadioButton
    {
        public JRadioButtonNoGuiUtils(String s)
        {
            super(s);
        }

        @Override
        public void setEnabled(boolean b)
        {
            // the hack so good we had to use it thrice
            if(!Reflection.getCallerClass(3).toString().matches(".*GuiUtils.*"))
            {
                super.setEnabled(b);
            }
        }
    }
}