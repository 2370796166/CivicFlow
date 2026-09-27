package com.civicflow.dev;

import java.nio.file.Files;
import java.nio.file.Path;

/** IDEA's built-in JAR runner entry point; no optional IDE plugins required. */
public final class LocalLauncher {
    private LocalLauncher() {}

    public static void main(String[] args) throws Exception {
        String action = args.length == 0 ? "up" : args[0];
        if (!java.util.Set.of("up", "prepare", "stop", "status").contains(action)) {
            throw new IllegalArgumentException("Expected up, prepare, stop or status");
        }
        Path script = Path.of("scripts", "dev.ps1").toAbsolutePath();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Working directory must be the CivicFlow project root");
        }
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-File",
                script.toString(), action).inheritIO().start();
        System.exit(process.waitFor());
    }
}
