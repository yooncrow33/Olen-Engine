package com.fw.main.utils.platform.system.scene;

import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.Base;
import com.fw.main.Base.LoadAction;
import com.fw.main.Base.LoadTask;
import com.fw.main.Core;
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.input.mouse.MouseInterface;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Scene implements MouseInterface {
    final ArrayList<Sfx> soundAssetLives = new ArrayList<>();
    final ArrayList<Bgm> musicAssetLives = new ArrayList<>();
    final Map<Integer, Atlas> atlasMap = new ConcurrentHashMap<>();

    final List<LoadTask> pendingLoadTasks = new ArrayList<>();
    final List<String> boundLazyEvents = new ArrayList<>();

    public volatile Base base;
    public String name = getClass().getSimpleName();

    // ═══════════════════════════════════════════════════════════════════
    // Save / Load (User override area)
    // ═══════════════════════════════════════════════════════════════════

    /** If set to true, the engine automatically calls load upon scene entry and save upon engine shutdown/save */
    public boolean saveable = false;

    public boolean canSave() {
        return saveable;
    }

    /** Write persistent data into props */
    protected void save(Properties props) { }

    /** Restore persistent data from props */
    protected void load(Properties props) { }

    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle and Hooks
    // ═══════════════════════════════════════════════════════════════════

    public abstract void init(SceneInit init) throws Exception;
    public abstract void update(double dt);
    public abstract void render(Graphics2D g);

    /** Called right before the scene becomes active after loading completes */
    protected void onEnter() { }

    /** Called when the scene begins transitioning out of the screen */
    protected void onExit() { }

    /** Called when scene resources are completely released from VRAM and memory */
    protected void onDispose() { }

    // ═══════════════════════════════════════════════════════════════════
    // SceneInit and Internal Engine Controller
    // ═══════════════════════════════════════════════════════════════════

    public final SceneInit sceneInit = new SceneInit(this);
    public final DO_NOT_ACCESS DO_NOT_ACCESS = new DO_NOT_ACCESS(this);

    protected Scene() {}

    protected Scene(String name) {
        if (name != null && !name.isEmpty()) this.name = name;
    }

    synchronized void disposeInternal() {
        try { onExit(); } catch (Exception ignored) {}
        try { onDispose(); } catch (Exception ignored) {}

        for (Sfx s : soundAssetLives) {
            if (s != null && s.getSoundAsset() != null) {
                s.getSoundAsset().stop();
                s.getSoundAsset().free();
            }
        }
        soundAssetLives.clear();

        for (Bgm b : musicAssetLives) {
            if (b != null && b.getMusicAsset() != null) {
                b.getMusicAsset().stop();
                b.getMusicAsset().free();
            }
        }
        musicAssetLives.clear();

        for (Atlas a : atlasMap.values()) {
            if (a != null) a.free();
        }
        atlasMap.clear();

        pendingLoadTasks.clear();
        boundLazyEvents.clear();
    }

    int getRandomId() {
        int i;
        do { i = InternalUtils.getRandom().nextInt(); } while (atlasMap.containsKey(i));
        return i;
    }

    @FunctionalInterface
    public interface AtlasBinderCallback {
        void execute(AtlasBinder binder) throws Exception;
    }

    public static class AtlasBinder {
        final List<TempTexture> tempTextures = new ArrayList<>();
        final Map<String, Sprite> pendingSprites = new ConcurrentHashMap<>();

        public Sprite registerSprite(InputStream is, String key) throws Exception {
            tempTextures.add(new TempTexture(is, key));
            Sprite proxy = new Sprite(key);
            pendingSprites.put(key, proxy);
            return proxy;
        }

        public Sprite registerSprite(BufferedImage src, int targetW, int targetH, String key) {
            BufferedImage resized = resizeImage(src, targetW, targetH);
            tempTextures.add(new TempTexture(resized, key));
            Sprite proxy = new Sprite(key);
            pendingSprites.put(key, proxy);
            return proxy;
        }

        private BufferedImage resizeImage(BufferedImage src, int targetW, int targetH) {
            if (src.getWidth() == targetW && src.getHeight() == targetH) return src;
            BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, targetW, targetH, null);
            g.dispose();
            return dst;
        }

        @Internal public List<TempTexture> getTempTextures() { return tempTextures; }
        @Internal public Map<String, Sprite> getPendingSprites() { return pendingSprites; }
    }

    public class SceneInit {
        final Scene scene;
        SceneInit(Scene scene) { this.scene = scene; }

        public Sfx registerSound(InputStream is) {
            Sfx sfx = new Sfx(InternalSoundModule.loadSound(is), scene);
            scene.soundAssetLives.add(sfx);
            return sfx;
        }

        public Bgm registerMusic(InputStream is, boolean isStream) {
            Bgm bgm = new Bgm(InternalSoundModule.loadMusic(is, isStream), scene);
            scene.musicAssetLives.add(bgm);
            return bgm;
        }

        public Atlas createAtlas(String name, int padding, AtlasBinderCallback callback) throws Exception {
            AtlasBinder binder = new AtlasBinder();
            callback.execute(binder);

            int id = scene.getRandomId();
            Atlas atlas = Atlas.INTERNAL.createEmpty(name, scene.base, id, binder.pendingSprites);
            scene.atlasMap.put(id, atlas);

            if (!binder.tempTextures.isEmpty()) {
                scene.pendingLoadTasks.add(new LoadTask("Baking Atlas: " + name, () -> {
                    atlas.DO_NOT_ACCESS.bake(binder.tempTextures, binder.pendingSprites, padding);
                }));
            }
            return atlas;
        }

        public void addTask(String description, LoadAction action) {
            if (description == null || action == null) return;
            scene.pendingLoadTasks.add(new LoadTask(description, action));
        }

        public void bindLazyEvent(String eventKey) {
            if (eventKey != null && !scene.boundLazyEvents.contains(eventKey)) {
                scene.boundLazyEvents.add(eventKey);
            }
        }
    }

    @Internal
    public final class DO_NOT_ACCESS {
        private final Scene scene;
        private DO_NOT_ACCESS(Scene scene) { this.scene = scene; }

        public void setBase(Base base) { scene.base = base; }
        public void setName(String name) { if (name != null) scene.name = name; }
        public SceneInit getSceneInit() { return scene.sceneInit; }
        public List<LoadTask> getPendingLoadTasks() { return scene.pendingLoadTasks; }
        public List<String> getBoundLazyEvents() { return scene.boundLazyEvents; }

        public void prepareForLoad() {
            scene.pendingLoadTasks.clear();
            scene.boundLazyEvents.clear();
        }

        public void notifyEnter() {
            try {
                scene.onEnter();
            } catch (Exception e) {
                if (scene.base != null) {
                    scene.base.addErrorLog("Error in Scene onEnter [" + scene.name + "]: " + e.getMessage());
                }
            }
        }

        public void save() {
            if (!scene.canSave()) return;
            Properties props = new Properties();
            scene.save(props);
            File f = SceneStorage.saveFile(scene.name);
            try (OutputStream os = new FileOutputStream(f)) {
                props.store(os, "Scene: " + scene.name);
            } catch (Exception e) {
                if (scene.base != null)
                    scene.base.addErrorLog("Scene save failed [" + scene.name + "]: " + e.getMessage());
            }
        }

        public void load() {
            if (!scene.canSave()) return;
            File f = SceneStorage.saveFile(scene.name);
            if (!f.exists()) return;
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(f)) {
                props.load(is);
            } catch (Exception e) {
                if (scene.base != null)
                    scene.base.addErrorLog("Scene load failed [" + scene.name + "]: " + e.getMessage());
                return;
            }
            scene.load(props);
        }

        public void dispose() { scene.disposeInternal(); }
    }

    static final class SceneStorage {
        static File saveFile(String sceneName) {
            String home = System.getProperty("user.home");
            String proj = Core.get().getProjectName();
            File dir = new File(home + File.separator + "." + proj + File.separator + "save");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, sceneName + ".properties");
        }
    }

    // --- Mouse Bridge ---
    @Override public final void mouseClicked(FwMouseAPI fwMouseAPI) { mClicked(fwMouseAPI); }
    @Override public final void mousePressed(FwMouseAPI fwMouseAPI) { mPressed(fwMouseAPI); }
    @Override public final void mouseReleased(FwMouseAPI fwMouseAPI) { mReleased(fwMouseAPI); }
    @Override public final void mouseEntered(FwMouseAPI fwMouseAPI) { mEntered(fwMouseAPI); }
    @Override public final void mouseExited(FwMouseAPI fwMouseAPI) { mExited(fwMouseAPI); }
    @Override public final void mouseWheelMoved(FwMouseAPI fwMouseAPI) { mWheelMoved(fwMouseAPI); }
    @Override public final void mouseDragged(FwMouseAPI e) { mDragged(e); }
    @Override public final void mouseMoved(FwMouseAPI e) { mMoved(e); }

    public void mClicked(FwMouseAPI e) {}
    public void mPressed(FwMouseAPI e) {}
    public void mReleased(FwMouseAPI e) {}
    public void mEntered(FwMouseAPI e) {}
    public void mExited(FwMouseAPI e) {}
    public void mWheelMoved(FwMouseAPI e) {}
    public void mDragged(FwMouseAPI e) {}
    public void mMoved(FwMouseAPI e) {}

    // --- Key Stubs ---
    public void onKeyAPress() {} public void onKeyBPress() {} public void onKeyCPress() {}
    public void onKeyDPress() {} public void onKeyEPress() {} public void onKeyFPress() {}
    public void onKeyGPress() {} public void onKeyHPress() {} public void onKeyIPress() {}
    public void onKeyJPress() {} public void onKeyKPress() {} public void onKeyLPress() {}
    public void onKeyMPress() {} public void onKeyNPress() {} public void onKeyOPress() {}
    public void onKeyPPress() {} public void onKeyQPress() {} public void onKeyRPress() {}
    public void onKeySPress() {} public void onKeyTPress() {} public void onKeyUPress() {}
    public void onKeyVPress() {} public void onKeyWPress() {} public void onKeyXPress() {}
    public void onKeyYPress() {} public void onKeyZPress() {}
    public void onKey0Press() {} public void onKey1Press() {} public void onKey2Press() {}
    public void onKey3Press() {} public void onKey4Press() {} public void onKey5Press() {}
    public void onKey6Press() {} public void onKey7Press() {} public void onKey8Press() {}
    public void onKey9Press() {}
    public void onKeyF1Press() {} public void onKeyF2Press() {} public void onKeyF3Press() {}
    public void onKeyF4Press() {} public void onKeyF5Press() {} public void onKeyF6Press() {}
    public void onKeyF7Press() {} public void onKeyF8Press() {} public void onKeyF9Press() {}
    public void onKeyF10Press() {} public void onKeyF11Press() {} public void onKeyF12Press() {}
    public void onKeyShiftPress() {} public void onKeyCtrlPress() {} public void onKeyAltPress() {}
    public void onKeySpacePress() {} public void onKeyEnterPress() {} public void onKeyEscPress() {}
    public void onKeyTabPress() {} public void onKeyBackspacePress() {}
    public void onKeyUpPress() {} public void onKeyDownPress() {}
    public void onKeyLeftPress() {} public void onKeyRightPress() {}

    public void onKeyARelease() {} public void onKeyBRelease() {} public void onKeyCRelease() {}
    public void onKeyDRelease() {} public void onKeyERelease() {} public void onKeyFRelease() {}
    public void onKeyGRelease() {} public void onKeyHRelease() {} public void onKeyIRelease() {}
    public void onKeyJRelease() {} public void onKeyKRelease() {} public void onKeyLRelease() {}
    public void onKeyMRelease() {} public void onKeyNRelease() {} public void onKeyORelease() {}
    public void onKeyPRelease() {} public void onKeyQRelease() {} public void onKeyRRelease() {}
    public void onKeySRelease() {} public void onKeyTRelease() {} public void onKeyURelease() {}
    public void onKeyVRelease() {} public void onKeyWRelease() {} public void onKeyXRelease() {}
    public void onKeyYRelease() {} public void onKeyZRelease() {}
    public void onKey0Release() {} public void onKey1Release() {} public void onKey2Release() {}
    public void onKey3Release() {} public void onKey4Release() {} public void onKey5Release() {}
    public void onKey6Release() {} public void onKey7Release() {} public void onKey8Release() {}
    public void onKey9Release() {}
    public void onKeyF1Release() {} public void onKeyF2Release() {} public void onKeyF3Release() {}
    public void onKeyF4Release() {} public void onKeyF5Release() {} public void onKeyF6Release() {}
    public void onKeyF7Release() {} public void onKeyF8Release() {} public void onKeyF9Release() {}
    public void onKeyF10Release() {} public void onKeyF11Release() {} public void onKeyF12Release() {}
    public void onKeyShiftRelease() {} public void onKeyCtrlRelease() {} public void onKeyAltRelease() {}
    public void onKeySpaceRelease() {} public void onKeyEnterRelease() {} public void onKeyEscRelease() {}
    public void onKeyTabRelease() {} public void onKeyBackspaceRelease() {}
    public void onKeyUpRelease() {} public void onKeyDownRelease() {}
    public void onKeyLeftRelease() {} public void onKeyRightRelease() {}
}