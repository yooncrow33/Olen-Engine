package com.fw.main.utils.platform.system.asset;

import com.fw.main.Core;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.console.Console;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.io.InputStream;

class PooledTexture implements Texture {
    private final GraphicsConfiguration config;
    private final AssetManager assetManager;
    private volatile String assetKey;
    private volatile InputStream assetStream;
    private volatile boolean closed = false;
    private volatile boolean inUse = false;

    private BufferedImage backupImage;
    private VolatileImage volatileImage;
    private int width;
    private int height;

    PooledTexture(AssetManager assetManager) {
        this.config = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        this.assetManager = assetManager;
    }

    void init(String assetKey, InputStream assetStream) {
        this.assetKey = assetKey;
        this.assetStream = assetStream;
        this.inUse = true;
        this.closed = false;
    }

    void setInUse(boolean use) { this.inUse = use; }

    void loadData() {
        if (closed) return;

        BufferedImage tempImg = null;
        if (this.assetStream != null) {
            try (InputStream is = this.assetStream) {
                tempImg = ImageIO.read(is);
            } catch (IOException e) {
                if (!Core.get().isSafeRuntime()) {
                    throw new RuntimeException("Texture I/O fail: " + this.assetKey, e);
                }
                assetManager.instance.getConsole().addLog(Console.LogType.SAFE_RUNTIME, "I/O error, instead texture at: " + assetKey);
            } finally {
                this.assetStream = null;
            }
        }

        if (tempImg == null) {
            if (Core.get().isSafeRuntime()) {
                tempImg = assetManager.wrongTexture;
                assetManager.instance.getConsole().addLog(Console.LogType.SAFE_RUNTIME, "Invalid format or null stream, instead texture at: " + assetKey);
            } else {
                throw new RuntimeException("Texture load fail: " + this.assetKey);
            }
        }

        this.width = tempImg.getWidth();
        this.height = tempImg.getHeight();
        this.backupImage = tempImg;
    }

    private void createAndCopyVolatileImage() {
        this.volatileImage = config.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT);
        copyToVolatile();
    }

    private synchronized void copyToVolatile() {
        if (closed) {
            return;
        }
        if (backupImage == null) {
            backupImage = assetManager.wrongTexture;
        }
        Graphics2D g = volatileImage.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(backupImage, 0, 0, null);
        g.dispose();
    }

    @Override
    public VolatileImage getVolatileImage() {
        if (volatileImage == null) {
            createAndCopyVolatileImage();
            return volatileImage;
        }
        int validateCode = volatileImage.validate(config);
        if (validateCode == VolatileImage.IMAGE_INCOMPATIBLE) {
            createAndCopyVolatileImage();
        } else if (validateCode == VolatileImage.IMAGE_RESTORED) {
            copyToVolatile();
        }
        return volatileImage;
    }

    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public String getAssetKey() { return assetKey; }
    @Override public boolean isInUse() { return inUse; }
    @Override public AssetManager.AssetType getAssetType() { return AssetManager.AssetType.TEXTURE; }

    @Override
    public void flush() {
        if (volatileImage != null) {
            volatileImage.flush();
            volatileImage = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        flush();
        if (backupImage != null) {
            if (backupImage != assetManager.wrongTexture) {
                backupImage.flush();
            }
            backupImage = null;
        }
        inUse = false;
        assetKey = null;
        assetStream = null;
    }
}