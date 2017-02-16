/* ==========================================================
File:        CustomDocumentListener.java
Description: Logs time from document change events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package io.jetbrains.opzio;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.math.BigDecimal;

public class CustomDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        final FileDocumentManager instance = FileDocumentManager.getInstance();
        final VirtualFile file = instance.getFile(documentEvent.getDocument());
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