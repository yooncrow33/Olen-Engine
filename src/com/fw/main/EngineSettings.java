package com.fw.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads and writes one engine setting per properties file. */
final class EngineSettings {
    private EngineSettings() { }

    static Path directory(String projectName) {
        return Path.of(System.getProperty("user.home"), "." + projectName, "EngineSettings");
    }

    static Path file(String projectName, String name) {
        return directory(projectName).resolve(name + ".properties");
    }

    static String read(String projectName, String name, String fallback) {
        Path path = file(projectName,name);
        if (!Files.isRegularFile(path)) return fallback;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties.getProperty("value",fallback);
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("Cannot read engine setting " + path + ": " + error.getMessage());
            return fallback;
        }
    }

    static void write(String projectName, String name, String value) {
        Path path = file(projectName,name);
        Properties properties = new Properties();
        properties.setProperty("value",value == null ? "" : value);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output,"Engine setting");
            }
        } catch (IOException error) {
            System.err.println("Cannot save engine setting " + path + ": " + error.getMessage());
        }
    }

    /** Removes saved values; startup code supplies defaults on the next launch. */
    static void reset(String projectName) throws IOException {
        clearDirectory(directory(projectName));
        clearDirectory(Path.of(System.getProperty("user.home"), "." + projectName, "엔진세팅"));
    }

    private static void clearDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file) &&
                        (file.getFileName().toString().endsWith(".properties") ||
                                file.getFileName().toString().equals("loadingScreenTexture.bin")))
                    Files.delete(file);
            }
        }
    }
}
