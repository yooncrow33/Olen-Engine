package com.fw.main.utils.platform.system.asset;

import java.awt.image.VolatileImage;

public class BootTextureProxy implements Texture {
    private final AssetManager assetManager;
    private final String key;
    private volatile Texture target;

    public BootTextureProxy(AssetManager assetManager, String key) {
        this.assetManager = assetManager;
        this.key = key;
    }

    
    public void setTarget(Texture target) {
        this.target = target;
    }

    @Override
    public synchronized void close() {
        if (assetManager != null && key != null) {
            assetManager.free(AssetManager.AssetType.TEXTURE, key);
        }
        this.target = null;
    }

    @Override
    public VolatileImage getVolatileImage() {
        Texture t = this.target;
        return t != null ? t.getVolatileImage() : null;
    }

    @Override
    public int getWidth() {
        Texture t = this.target;
        return t != null ? t.getWidth() : 0;
    }

    @Override
    public int getHeight() {
        Texture t = this.target;
        return t != null ? t.getHeight() : 0;
    }

    @Override
    public String getAssetKey() {
        Texture t = this.target;
        return t != null ? t.getAssetKey() : null;
    }

    @Override
    public boolean isInUse() {
        Texture t = this.target;
        return t != null && t.isInUse();
    }

    @Override
    public void flush() {
        Texture t = this.target;
        if (t != null) t.flush();
    }

    @Override
    public AssetManager.AssetType getAssetType() {
        return AssetManager.AssetType.TEXTURE;
    }
}