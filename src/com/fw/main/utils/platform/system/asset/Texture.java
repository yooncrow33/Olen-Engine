package com.fw.main.utils.platform.system.asset;

import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

public interface Texture extends AutoCloseable {
    VolatileImage getVolatileImage();
    BufferedImage getBufferedImage();
    int getWidth();
    int getHeight();
    String getAssetKey();
    boolean isInUse();
    void flush();
    @Override
    void close();
    AssetManager.AssetType getAssetType();
}