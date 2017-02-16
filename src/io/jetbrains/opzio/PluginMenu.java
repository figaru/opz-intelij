package io.jetbrains.opzio;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class PluginMenu extends AnAction {
    public PluginMenu() {
        super("Opzio Settings");
        // super("Opzio Settings", "", IconLoader.getIcon("/Mypackage/icon.png"));
    }
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Settings popup = new Settings(project);
        popup.show();
    }
}
