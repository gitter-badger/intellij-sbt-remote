package com.dancingrobot84.sbt.remote.external.controls;

import com.dancingrobot84.sbt.remote.Bundle;
import com.dancingrobot84.sbt.remote.external.ExternalSystemManager;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectPathField;
import com.intellij.openapi.project.Project;

import com.dancingrobot84.sbt.remote.external.package$;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author Nikolay Obedin
 * @since 3/31/15.
 */
public class TaskRunConfigurationForm {
    private ExternalProjectPathField myExternalProjectPathField;
    private JPanel myMainPanel;
    private JPanel myTasksPanel;
    private JCheckBox myRunAsIsCheckBox;
    private JList myTasksList;
    private Project myProject;
    private CollectionListModel<String> myTasksModel = new CollectionListModel<String>();

    public TaskRunConfigurationForm(Project project) {
        myProject = project;
        $$$setupUI$$$();
    }

    private void createUIComponents() {
        myExternalProjectPathField = new ExternalProjectPathField(
                myProject, package$.MODULE$.Id(),
                new ExternalSystemManager().getExternalProjectDescriptor(),
                Bundle.apply("sbt.remote.name"));

        myTasksList = new JBList(myTasksModel);
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTasksList);

        decorator.setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                String task = Messages.showInputDialog(
                        myTasksList, Bundle.apply("sbt.remote.task.enterName"),
                        Bundle.apply("sbt.remote.task.addTask"),
                        null);
                if (task != null) {
                    myTasksModel.add(task);
                }
            }
        });
        decorator.setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                myTasksModel.remove(myTasksList.getSelectedIndex());
            }
        });

        myTasksPanel = decorator.createPanel();
    }

    public JComponent getMainPanel() {
        return myMainPanel;
    }

    public List<String> getTasks() {
        return myTasksModel.getItems();
    }

    public void setTasks(List<String> tasks) {
        myTasksModel.removeAll();
        myTasksModel.add(tasks);
    }

    public String getProjectPath() {
        return myExternalProjectPathField.getText();
    }

    public void setProjectPath(String path) {
        myExternalProjectPathField.setText(path);
    }

    public Boolean shouldRunAsIs() {
        return myRunAsIsCheckBox.isSelected();
    }

    public void setShouldRunAsIs(Boolean value) {
        myRunAsIsCheckBox.setSelected(value);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myMainPanel = new JPanel();
        myMainPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
        myMainPanel.add(myExternalProjectPathField, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myMainPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("com/dancingrobot84/sbt/remote/Bundle").getString("sbt.remote.task.project"));
        myMainPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, ResourceBundle.getBundle("com/dancingrobot84/sbt/remote/Bundle").getString("sbt.remote.task.tasks"));
        myMainPanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myMainPanel.add(myTasksPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myRunAsIsCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(myRunAsIsCheckBox, ResourceBundle.getBundle("com/dancingrobot84/sbt/remote/Bundle").getString("sbt.remote.task.runAsIs"));
        myMainPanel.add(myRunAsIsCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myMainPanel;
    }
}
