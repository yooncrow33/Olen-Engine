package com.fw.main.api.io;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DynamicIoLoadObject {
    private static final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();

    private final AtomicBoolean loadStart = new AtomicBoolean(false);
    private final AtomicBoolean loadEnd = new AtomicBoolean(false);
    private final String fullPath;
    private final DynamicIo interfaceObject;

    public DynamicIoLoadObject(String fullPath, DynamicIo dynamicIo) {
        this.fullPath = fullPath;
        interfaceObject = dynamicIo;
    }

    void internalLoad() {
        Properties p = new Properties();
        File file = new File(fullPath);
        if (!file.exists()) {
            System.err.println("Not found Path! at: " + fullPath);
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            interfaceObject.load(p);
        } catch (IOException | NumberFormatException e) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null, "Load fail: " + e.getMessage())
            );
        }
    }

    public void launch() {
        if (!loadStart.compareAndSet(false, true)) {
            return;
        }

        loadExecutor.submit(() -> {
            try {
                internalLoad();
            } finally {
                loadEnd.set(true);
            }
        });
    }

    public boolean isLoaded() {
        return loadEnd.get();
    }
}