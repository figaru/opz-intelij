package io.jetbrains.opzio;

import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.math.BigDecimal;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        final FileDocumentManager instance = FileDocumentManager.getInstance();
        final VirtualFile file = instance.getFile(visibleAreaEvent.getEditor().getDocument());
        if (file != null && !file.getUrl().startsWith("mock://")) {
            final String currentFile = file.getPath();
            if (Opzio.shouldLogFile(currentFile)) {
                BigDecimal currentTime = Opzio.getCurrentTimestamp();
                if (!currentFile.equals(Opzio.lastFile) || Opzio.enoughTimePassed(currentTime)) {
                    Opzio.appendHeartbeat(currentTime, currentFile, false);
                }
            }
        }
    }
}
