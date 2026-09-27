package com.fw.main;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Startup configuration shared by Base and Vase. */
public class Config {
    final String projectName, settingsProjectName;
    int initWindowWidth, initWindowHeight;
    boolean useKoreanModule, useEncryption, useIntegerPhysicalScaling;
    String secretKey, loadingScreenTexturePath;
    InputStream loadingScreenTexture;
    public final SecretKeySpec encryptionKey;

    public String getProjectName() { return projectName; }
    /** Returns the stable project identity used for the settings directory. */
    public String getSettingsProjectName() { return settingsProjectName; }
    public int getInitWindowWidth() { return initWindowWidth; }
    public int getInitWindowHeight() { return initWindowHeight; }
    public boolean isUseKoreanModule() { return useKoreanModule; }
    public boolean isUseEncryption() { return useEncryption; }
    public InputStream getLoadingScreenTexture() { return loadingScreenTexture; }
    public String getLoadingScreenTexturePath() { return loadingScreenTexturePath; }
    public boolean isUseIntegerPhysicalScaling() { return useIntegerPhysicalScaling; }
    public String getEncryptionKey() { return secretKey; }

    private Config(Builder builder) {
        projectName = builder.projectName;
        settingsProjectName = builder.settingsProjectName;
        initWindowWidth = builder.initWindowWidth;
        initWindowHeight = builder.initWindowHeight;
        useKoreanModule = builder.useKoreanModule;
        useEncryption = builder.useEncryption;
        useIntegerPhysicalScaling = builder.useIntegerPhysicalScaling;
        secretKey = builder.secretKey == null ? "" : builder.secretKey;
        loadingScreenTexturePath = builder.loadingScreenTexturePath;
        loadingScreenTexture = builder.loadingScreenTexture;
        byte[] keyBytes = secretKey.getBytes();
        encryptionKey = new SecretKeySpec(keyBytes.length == 0 ? new byte[]{0} : keyBytes,"AES");
    }

    /** Collects startup values and loads saved properties when built. */
    public static class Builder {
        final String settingsProjectName;
        String projectName;
        int initWindowWidth = 1200, initWindowHeight = 300;
        boolean useKoreanModule, useEncryption, useIntegerPhysicalScaling;
        String secretKey = "qwerasdfzxcvtyui", loadingScreenTexturePath;
        InputStream loadingScreenTexture;
        boolean preferSavedSettings = true;

        public Builder(String projectName) {
            settingsProjectName = java.util.Objects.requireNonNull(projectName);
            this.projectName = projectName;
        }
        public Builder setPreferSavedSettings(boolean enabled) { preferSavedSettings = enabled; return this; }
        public Builder setProjectName(String value) { projectName = value; return this; }
        public Builder setWindowWidth(int value) { initWindowWidth = value; return this; }
        public Builder setWindowHeight(int value) { initWindowHeight = value; return this; }
        public Builder setUseKoreanModule(boolean value) { useKoreanModule = value; return this; }
        public Builder setUseEncryption(boolean value) { useEncryption = value; return this; }
        /** Controls integer scaling of the final Graphics2D device transform. */
        public Builder setUseIntegerPhysicalScaling(boolean value) { useIntegerPhysicalScaling = value; return this; }
        public Builder setEncryptionKey(String value) { secretKey = value; return this; }
        public Builder setLoadingScreenTexture(InputStream value) { loadingScreenTexture = value; loadingScreenTexturePath = null; return this; }
        /** Loads a texture from a file when the engine starts. */
        public Builder setLoadingScreenTexturePath(String value) { loadingScreenTexturePath = value; loadingScreenTexture = null; return this; }

        public Config build() {
            if (preferSavedSettings) loadSavedValues();
            if (loadingScreenTexturePath != null && loadingScreenTexturePath.isEmpty()) loadingScreenTexturePath = null;
            loadTexture();
            saveValues();
            return new Config(this);
        }
        private void loadSavedValues() {
            initWindowWidth = savedInt("initWindowWidth",initWindowWidth);
            initWindowHeight = savedInt("initWindowHeight",initWindowHeight);
            useKoreanModule = savedBoolean("useKoreanModule",useKoreanModule);
            useIntegerPhysicalScaling = savedBoolean("useIntegerPhysicalScaling",useIntegerPhysicalScaling);
            String path = read("loadingScreenTexturePath",loadingScreenTexturePath == null ? "" : loadingScreenTexturePath);
            loadingScreenTexturePath = path.isEmpty() ? null : path;
        }
        private void loadTexture() {
            if (loadingScreenTexturePath != null) {
                try { loadingScreenTexture = Files.newInputStream(Path.of(loadingScreenTexturePath)); }
                catch (IOException | RuntimeException error) {
                    System.err.println("Cannot load loading screen texture: " + error.getMessage());
                    loadingScreenTexture = null;
                }
            } else if (loadingScreenTexture != null) {
                try {
                    byte[] bytes = loadingScreenTexture.readAllBytes();
                    Files.createDirectories(EngineSettings.directory(settingsProjectName));
                    Path path = EngineSettings.directory(settingsProjectName).resolve("loadingScreenTexture.bin");
                    Files.write(path,bytes);
                    loadingScreenTexturePath = path.toString();
                    loadingScreenTexture = new ByteArrayInputStream(bytes);
                } catch (IOException error) { System.err.println("Cannot save loading screen texture: " + error.getMessage()); }
            }
        }
        private void saveValues() {
            write("initWindowWidth",Integer.toString(initWindowWidth));
            write("initWindowHeight",Integer.toString(initWindowHeight));
            write("useKoreanModule",Boolean.toString(useKoreanModule));
            write("useIntegerPhysicalScaling",Boolean.toString(useIntegerPhysicalScaling));
            write("loadingScreenTexturePath",loadingScreenTexturePath);
        }
        private String read(String name,String fallback) { return EngineSettings.read(settingsProjectName,"core."+name,fallback); }
        private void write(String name,String value) { EngineSettings.write(settingsProjectName,"core."+name,value); }
        private int savedInt(String name,int fallback) {
            try { return Integer.parseInt(read(name,Integer.toString(fallback))); }
            catch (NumberFormatException error) { return fallback; }
        }
        private boolean savedBoolean(String name,boolean fallback) {
            String value = read(name,Boolean.toString(fallback));
            return "true".equalsIgnoreCase(value) ? true : "false".equalsIgnoreCase(value) ? false : fallback;
        }
    }
}
