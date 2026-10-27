package com.fw.main.utils.platform.system.scene;


import com.fw.main.Core;
import com.fw.main.utils.graphics.simple.Tex;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.Texture;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;

public class Sprite {
    public int x, y;
    public int w, h;

    public int trimX, trimY;
    public int originalW, originalH;

    public String key;
    private Atlas atlas;

    public static BufferedImage wrongTexture;

    static {
        try {
            wrongTexture = ImageIO.read(IoUtils.getGameResourceStream("WrongTexture.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Sprite(String key) {
        this.key = key;
    }

    Sprite(String key, int x, int y, Atlas.CroppedImage cropped, Atlas atlas) {
        this.key = key; this.atlas = atlas;
        this.x = x; this.y = y;
        this.w = cropped.cropW; this.h = cropped.cropH;
        this.trimX = cropped.trimX; this.trimY = cropped.trimY;
        this.originalW = cropped.originalW; this.originalH = cropped.originalH;
    }

    void initData(Sprite packed) {
        this.atlas = packed.atlas;
        this.x = packed.x;
        this.y = packed.y;
        this.w = packed.w;
        this.h = packed.h;
        this.trimX = packed.trimX;
        this.trimY = packed.trimY;
        this.originalW = packed.originalW;
        this.originalH = packed.originalH;
    }

    
    public void draw(Graphics2D g, int destX, int destY, int destW, int destH) {
        VolatileImage vImg = atlas.getVolatileImage();

        int renderX = destX + (trimX * destW) / originalW;
        int renderY = destY + (trimY * destH) / originalH;
        int renderW = (w * destW) / originalW;
        int renderH = (h * destH) / originalH;

        if (vImg == null || destW <= 0 || destH <= 0 || originalW <= 0 || originalH <= 0 && Core.get().isSafeRuntime()) {
            g.drawImage(
                    wrongTexture,
                    renderX, renderY, renderX + renderW, renderY + renderH,
                    x, y, x + w, y + h,
                    null
            );
        }

        g.drawImage(
                vImg,
                renderX, renderY, renderX + renderW, renderY + renderH,
                x, y, x + w, y + h,
                null
        );
    }

    public void drawRawPx(Graphics2D g, int destX, int destY) {
        VolatileImage vImg = atlas.getVolatileImage();
        int renderX = destX + trimX;
        int renderY = destY + trimY;

        if (vImg == null && Core.get().isSafeRuntime()) {
            g.drawImage(
                    wrongTexture,
                    renderX, renderY, renderX + w, renderY + h,
                    x, y, x + w, y + h,
                    null
            );
        }

        g.drawImage(
                vImg,
                renderX, renderY, renderX + w, renderY + h,
                x, y, x + w, y + h,
                null
        );
    }
}
