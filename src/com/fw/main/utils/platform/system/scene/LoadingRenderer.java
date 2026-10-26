package com.fw.main.utils.platform.system.scene;

import java.awt.Graphics2D;

@FunctionalInterface
public interface LoadingRenderer {
    void render(Graphics2D g, float progress, String message, int screenWidth, int screenHeight);
}