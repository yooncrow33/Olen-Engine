package com.fw.main.utils.platform.system.scene;

import com.fw.internal.utils.Internal;
import com.fw.main.Base;
import com.fw.main.Fw;
import com.fw.main.utils.platform.system.console.Console;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Atlas {
    private GraphicsConfiguration config;

    private static final int MAX_SIZE = 4096;
    private int atlasWidth, atlasHeight;
    final Map<String, Sprite> spriteMap = new ConcurrentHashMap<>();
    private final String name;
    private volatile boolean isFreed = false;
    private volatile boolean isBaked = false;
    private final Base base;
    private final int id;

    long totalOriginalArea = 0;
    long totalCroppedArea = 0;

    private BufferedImage backupImage;
    private VolatileImage volatileImage;

    Atlas(String name, Base base, int id, Map<String, Sprite> pendingSprites) {
        this.config = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        this.name = name;
        this.base = base;
        this.id = id;
        if (pendingSprites != null) {
            this.spriteMap.putAll(pendingSprites);
        }
    }

    Atlas(List<TempTexture> tempList, Map<String, Sprite> pendingSprites, int padding, String name, Base base, int id) throws Exception {
        this(name, base, id, pendingSprites);
        bake(tempList, pendingSprites, padding);
    }

    synchronized void bake(List<TempTexture> tempList, Map<String, Sprite> pendingSprites, int padding) throws Exception {
        if (isBaked || isFreed) return;

        if (tempList == null || tempList.isEmpty()) {
            this.atlasWidth = 0;
            this.atlasHeight = 0;
            this.isBaked = true;
            return;
        }

        List<CroppedImage> croppedList = new ArrayList<>();
        for (TempTexture input : tempList) {
            input.loadData();
            croppedList.add(cropAlpha(input));
        }

        croppedList.sort((a, b) -> {
            int maxA = Math.max(a.cropW + padding, a.cropH + padding);
            int maxB = Math.max(b.cropW + padding, b.cropH + padding);
            if (maxA != maxB) return Integer.compare(maxB, maxA);

            int areaA = (a.cropW + padding) * (a.cropH + padding);
            int areaB = (b.cropW + padding) * (b.cropH + padding);
            return Integer.compare(areaB, areaA);
        });

        int maxW = croppedList.stream().mapToInt(c -> c.cropW).max().orElse(0);
        int maxH = croppedList.stream().mapToInt(c -> c.cropH).max().orElse(0);
        int totalArea = croppedList.stream().mapToInt(c -> (c.cropW + padding) * (c.cropH + padding)).sum();

        List<SizeConfig> sizeConfigs = new ArrayList<>();
        for (int w = 16; w <= MAX_SIZE; w <<= 1) {
            for (int h = 16; h <= MAX_SIZE; h <<= 1) {
                sizeConfigs.add(new SizeConfig(w, h));
            }
        }

        sizeConfigs.sort((a, b) -> {
            int areaCompare = Integer.compare(a.area(), b.area());
            if (areaCompare != 0) return areaCompare;
            return Integer.compare(Math.abs(a.w - a.h), Math.abs(b.w - b.h));
        });

        List<Sprite> packedSprites = null;

        for (SizeConfig sizeCfg : sizeConfigs) {
            if (sizeCfg.w < maxW || sizeCfg.h < maxH) continue;
            if (sizeCfg.area() < totalArea / 2) continue;

            packedSprites = tryPack(croppedList, sizeCfg.w, sizeCfg.h, padding);
            if (packedSprites != null) {
                this.atlasWidth = sizeCfg.w;
                this.atlasHeight = sizeCfg.h;
                double efficiency = (double) totalArea / (sizeCfg.area()) * 100.0;
                String logMsg = String.format("Atlas [%s] Size: %dx%d, Packing Efficiency: %.2f%%", this.name, sizeCfg.w, sizeCfg.h, efficiency);
                if (base != null) {
                    Fw.Helper.getConsoleToBaseInstance(base).addLog(Console.LogType.SYSTEM, logMsg);
                }
                break;
            }
        }

        if (packedSprites != null) {
            for (Sprite packed : packedSprites) {
                Sprite userSprite = pendingSprites != null ? pendingSprites.get(packed.key) : null;
                if (userSprite != null) {
                    userSprite.initData(packed);
                    this.spriteMap.put(userSprite.key, userSprite);
                } else {
                    this.spriteMap.put(packed.key, packed);
                }
            }
        } else {
            throw new RuntimeException("Images exceed the maximum atlas size (" + MAX_SIZE + ").");
        }

        backupImage = new BufferedImage(getAtlasWidth(), getAtlasHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = backupImage.createGraphics();
        g.setComposite(AlphaComposite.Src);

        for (TempTexture input : tempList) {
            Sprite box = this.spriteMap.get(input.key);

            String logMsg = String.format("[Key: %-12s] Orig(%4dx%4d) -> Crop(%4dx%4d) | Pos(X:%4d, Y:%4d) | TrimOffset(X:%3d, Y:%3d)",
                    box.key, box.originalW, box.originalH, box.w, box.h, box.x, box.y, box.trimX, box.trimY);
            if (base != null) {
                Fw.Helper.getConsoleToBaseInstance(base).addLog(Console.LogType.SYSTEM, logMsg);
            }

            totalOriginalArea += (long) box.originalW * box.originalH;
            totalCroppedArea += (long) box.w * box.h;

            g.drawImage(
                    input.tempImg,
                    box.x, box.y, box.x + box.w, box.y + box.h,
                    box.trimX, box.trimY, box.trimX + box.w, box.trimY + box.h,
                    null
            );
        }
        g.dispose();

        for (TempTexture input : tempList) {
            if (input.tempImg != null) {
                input.tempImg.flush();
                input.tempImg = null;
            }
        }

        if (Fw.Debugger.atlasDebugger) {
            File outputFile = new File(this.name + "_atlas_output.png");
            ImageIO.write(backupImage, "png", outputFile);
        }

        this.isBaked = true;
    }

    @Internal
    public static class CroppedImage {
        String key;
        BufferedImage image;
        int trimX, trimY;
        int originalW, originalH;
        int cropW, cropH;

        CroppedImage(String key, BufferedImage image, int trimX, int trimY, int originalW, int originalH, int cropW, int cropH) {
            this.key = key;
            this.image = image;
            this.trimX = trimX;
            this.trimY = trimY;
            this.originalW = originalW;
            this.originalH = originalH;
            this.cropW = cropW;
            this.cropH = cropH;
        }
    }

    private List<Sprite> tryPack(List<CroppedImage> sortedImages, int binW, int binH, int padding) {
        List<FreeRect> freeRects = new ArrayList<>();
        freeRects.add(new FreeRect(0, 0, binW + padding, binH + padding));

        List<Sprite> result = new ArrayList<>();

        for (CroppedImage img : sortedImages) {
            int reqW = img.cropW + padding;
            int reqH = img.cropH + padding;

            int bestShortSide = Integer.MAX_VALUE;
            int bestLongSide = Integer.MAX_VALUE;
            FreeRect bestFree = null;

            for (FreeRect free : freeRects) {
                if (free.w >= reqW && free.h >= reqH) {
                    int leftoverW = free.w - reqW;
                    int leftoverH = free.h - reqH;
                    int shortSide = Math.min(leftoverW, leftoverH);
                    int longSide = Math.max(leftoverW, leftoverH);

                    if (shortSide < bestShortSide || (shortSide == bestShortSide && longSide < bestLongSide)) {
                        bestShortSide = shortSide;
                        bestLongSide = longSide;
                        bestFree = free;
                    }
                }
            }
            if (bestFree == null) return null;

            Sprite sprite = new Sprite(img.key, bestFree.x, bestFree.y, img, this);
            result.add(sprite);

            FreeRect usedSpace = new FreeRect(bestFree.x, bestFree.y, reqW, reqH);
            splitFreeRects(freeRects, usedSpace);
            pruneFreeRects(freeRects);
        }

        return result;
    }

    private void splitFreeRects(List<FreeRect> freeRects, FreeRect used) {
        List<FreeRect> newFree = new ArrayList<>();

        for (FreeRect free : freeRects) {
            if (used.x >= free.x + free.w || used.x + used.w <= free.x ||
                    used.y >= free.y + free.h || used.y + used.h <= free.y) {
                newFree.add(free);
                continue;
            }

            if (used.x > free.x)
                newFree.add(new FreeRect(free.x, free.y, used.x - free.x, free.h));
            if (used.x + used.w < free.x + free.w)
                newFree.add(new FreeRect(used.x + used.w, free.y, (free.x + free.w) - (used.x + used.w), free.h));
            if (used.y > free.y)
                newFree.add(new FreeRect(free.x, free.y, free.w, used.y - free.y));
            if (used.y + used.h < free.y + free.h)
                newFree.add(new FreeRect(free.x, used.y + used.h, free.w, (free.y + free.h) - (used.y + used.h)));
        }
        freeRects.clear();
        freeRects.addAll(newFree);
    }

    private void pruneFreeRects(List<FreeRect> freeRects) {
        for (int i = 0; i < freeRects.size(); i++) {
            for (int j = i + 1; j < freeRects.size(); j++) {
                FreeRect r1 = freeRects.get(i);
                FreeRect r2 = freeRects.get(j);

                if (contains(r2, r1)) {
                    freeRects.remove(i);
                    i--;
                    break;
                }
                if (contains(r1, r2)) {
                    freeRects.remove(j);
                    j--;
                }
            }
        }
    }

    private boolean contains(FreeRect outer, FreeRect inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
                inner.x + inner.w <= outer.x + outer.w &&
                inner.y + inner.h <= outer.y + outer.h;
    }

    public String getName() { return name; }
    public int getId() { return id; }
    public int getAtlasWidth() { return atlasWidth; }
    public int getAtlasHeight() { return atlasHeight; }

    public Sprite getSprite(String key) {
        return spriteMap.get(key);
    }

    public Map<String, Sprite> getSpriteMap() {
        return Collections.unmodifiableMap(spriteMap);
    }

    public float[] getUV(String key) {
        Sprite sprite = spriteMap.get(key);
        if (sprite == null || atlasWidth == 0 || atlasHeight == 0) return null;

        return new float[]{
                (float) sprite.x / atlasWidth,
                (float) sprite.y / atlasHeight,
                (float) (sprite.x + sprite.w) / atlasWidth,
                (float) (sprite.y + sprite.h) / atlasHeight
        };
    }

    private void createAndCopyVolatileImage() {
        if (getAtlasWidth() <= 0 || getAtlasHeight() <= 0) return;
        this.volatileImage = config.createCompatibleVolatileImage(getAtlasWidth(), getAtlasHeight(), Transparency.TRANSLUCENT);
        copyToVolatile();
    }

    private synchronized void copyToVolatile() {
        if (backupImage == null || volatileImage == null) return;

        Graphics2D g = volatileImage.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(backupImage, 0, 0, null);
        g.dispose();
    }

    public VolatileImage getVolatileImage() {
        if (isFreed || !isBaked || getAtlasWidth() <= 0 || getAtlasHeight() <= 0) return null;
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

    private CroppedImage cropAlpha(TempTexture input) {
        BufferedImage img = input.tempImg;
        int width = img.getWidth();
        int height = img.getHeight();

        int minX = width, minY = height, maxX = -1, maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >> 24) & 0xff;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX == -1) {
            return new CroppedImage(input.key, img.getSubimage(0, 0, 1, 1), 0, 0, width, height, 1, 1);
        }

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;

        BufferedImage cropped = img.getSubimage(minX, minY, cropW, cropH);

        return new CroppedImage(input.key, cropped, minX, minY, width, height, cropW, cropH);
    }

    void free() {
        isFreed = true;

        if (volatileImage != null) {
            volatileImage.flush();
            volatileImage = null;
        }

        if (backupImage != null) {
            backupImage.flush();
            backupImage = null;
        }

        spriteMap.clear();
    }

    public final DO_NOT_ACCESS DO_NOT_ACCESS = new DO_NOT_ACCESS(this);

    @Internal
    public static final class DO_NOT_ACCESS {
        private final Atlas atlas;

        private DO_NOT_ACCESS(Atlas atlas) {
            this.atlas = atlas;
        }

        public void bake(List<TempTexture> tempList, Map<String, Sprite> pendingSprites, int padding) throws Exception {
            atlas.bake(tempList, pendingSprites, padding);
        }

        public void free() {
            atlas.free();
        }
    }

    @Internal
    public static final class INTERNAL {
        public static Atlas createEmpty(String name, Base base, int id, Map<String, Sprite> pendingSprites) {
            return new Atlas(name, base, id, pendingSprites);
        }
    }

    private static class FreeRect {
        int x, y, w, h;
        FreeRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static class SizeConfig {
        int w, h;
        SizeConfig(int w, int h) {
            this.w = w;
            this.h = h;
        }
        int area() {
            return w * h;
        }
    }
}