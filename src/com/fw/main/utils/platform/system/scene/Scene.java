package com.fw.main.utils.platform.system.scene;

import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.Base;
import com.fw.main.utils.platform.system.asset.Sound;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class Scene {
    ArrayList<Sfx> soundAssetLives = new ArrayList<>();
    ArrayList<Bgm> musicAssetLives = new ArrayList<>();
    Map<Integer, Atlas> atlasMap = new ConcurrentHashMap<>();
    @Internal
    public volatile Base base;
    public final String name;
    final SceneAssetRegister assetRegister;
    SceneInit sceneInit = new SceneInit(this);

    public void init() throws Exception {
        assetRegister.init(sceneInit);
    }

    Scene(Builder builder) {
        this.assetRegister = builder.assetRegister;
        this.name = builder.name;
    }

    public interface AtlasBinderCallback {
        void execute(AtlasBinder binder) throws Exception;
    }

    public static class AtlasBinder {
        final List<TempTexture> tempTextures = new ArrayList<>();
        final Map<String, Sprite> pendingSprites = new ConcurrentHashMap<>();

        public Sprite registerSprite(InputStream is, String key) throws Exception {
            tempTextures.add(new TempTexture(is, key));

            //아틀라스가 이미지들을 읽고 패킹하기 전이기 때문에 이 스프라이트 객체는 껍대기입니다. 진짜는 아틀라스 객체 초기화시 됩니다.
            //게임엔진상 아틀라스 로딩되는 동안은 무조건 렌더링이 막히기 때문에 스프라이트의 실제 값이 호출될 수 없습니다. 안전합니다.
            Sprite proxy = new Sprite(key);
            pendingSprites.put(key, proxy);

            return proxy;
        }
    }

    public class SceneInit {
        final Scene scene;

        public SceneInit(Scene scene) {
            this.scene = scene;
        }

        public Sfx registerSound(InputStream is) {
            return new Sfx(InternalSoundModule.loadSound(is), scene);
        }

        public Bgm registerMusic(InputStream is, boolean isStream) {
            return new Bgm(InternalSoundModule.loadMusic(is, isStream), scene);
        }


        public void createAtlas(String name, int padding, AtlasBinderCallback callback) throws Exception {
            AtlasBinder binder = new AtlasBinder();

            callback.execute(binder);

            if (binder.tempTextures.isEmpty()) {
                return;
            }

            // 2. 수집된 텍스처들로 Atlas 생성 (Atlas 생성자 내부에서 pendingSprites 데이터 주입 필요)
            int id = scene.getRandomId();
            Atlas atlas = new Atlas(binder.tempTextures, binder.pendingSprites, padding, name, scene, id);

            scene.atlasMap.put(id, atlas);
        }
    }

    public interface SceneAssetRegister {
        void init(SceneInit sceneInit) throws Exception;
    }

    public static class Builder {
        String name = "not_set";
        final SceneAssetRegister assetRegister;

        public Builder(SceneAssetRegister assetRegister) {
            this.assetRegister = assetRegister;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Scene build() {
            return new Scene(this);
        }
    }

    //어쩔수 없이 Base에서 리플렉션
    private synchronized void dispose() {
        for (Sfx s : soundAssetLives) {
            s.getSoundAsset().stop();
            s.getSoundAsset().free();
        }
        soundAssetLives.clear();

        for (Bgm b : musicAssetLives) {
            b.getMusicAsset().stop();
            b.getMusicAsset().free();
        }
        musicAssetLives.clear();

        for (Atlas a : atlasMap.values()) {
            a.free();
        }
        atlasMap.clear();
    }

    /**
     * in scene.
     */
    int getRandomId() {
        int i;
        do {
            i = InternalUtils.getRandom().nextInt();
        } while (atlasMap.containsKey(i));

        return i;
    }
}