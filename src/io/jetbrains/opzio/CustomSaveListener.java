
package io.jetbrains.opzio;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;

import java.math.BigDecimal;

public class CustomSaveListener extends FileDocumentManagerAdapter {

    @Override
    public void beforeDocumentSaving(Document document) {
        String currentFile = FileDocumentManager.getInstance().getFile(document).getPath();
        if (Opzio.shouldLogFile(currentFile)) {
            BigDecimal currentTime = Opzio.getCurrentTimestamp();
            Opzio.appendHeartbeat(currentTime, currentFile, true);
        }
    }
}