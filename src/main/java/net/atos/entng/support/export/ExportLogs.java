package net.atos.entng.support.export;

import net.atos.entng.support.model.*;
import io.vertx.core.buffer.Buffer;

public class ExportLogs {

    private String logs;
    private final String name;

    public ExportLogs(String name) {
        this.logs = "";
        this.name = name;
    }

    public String logs() {
        return logs;
    }

    public void addLog(String log) {
        this.logs += log + "\n";
    }

    public ExportFile getLogFile() {
        Buffer buffer = Buffer.buffer(this.logs);
        return new ExportFile(buffer, "text/plain; charset=utf-8", this.name);
    }
}
