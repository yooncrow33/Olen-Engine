package com.fw.main.utils.platform.system.asset;

import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.Base;
import com.fw.main.Core;
import com.fw.main.Fw;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.MusicAsset;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.SoundAsset;
import com.fw.main.utils.platform.system.console.Console;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class AssetManager {
    public enum LoadMode {
        SYNC,
        LAZY
    }
    public enum MusicType {
        MEM_MUSIC,
        STREAM_MUSIC
    }

    public enum AssetType {
        SOUND,
        MUSIC,
        TEXTURE
    }

    final Base instance;
    private final Queue<PooledTexture> freePool = new ConcurrentLinkedQueue<>();
    private PooledTexture[] pool;
    private final Map<String, PooledTexture> textureActiveMap = new ConcurrentHashMap<>();
    private final Map<String, SoundAsset> soundActiveMap = new ConcurrentHashMap<>();
    private final Map<String, MusicAsset> musicActiveMap = new ConcurrentHashMap<>();
    private final Map<String, List<DynamicAssetObject>> pendingEvents = new ConcurrentHashMap<>();
    private final Map<String, DynamicAssetObject> pendingObjects = new ConcurrentHashMap<>();
    private final Queue<DynamicAssetObject> daoFreePool = new ConcurrentLinkedQueue<>();
    private DynamicAssetObject[] daoPool;
    final BufferedImage wrongTexture;

    public AssetManager(Base baseInstance) {
        this.instance = baseInstance;
        if (!InternalSoundModule.isInitialized()) {
            InternalSoundModule.init();
        }
        try {
            wrongTexture = ImageIO.read(IoUtils.getGameResourceStream("WrongTexture.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void mallocTexturePool(int capacity) {
        pool = new PooledTexture[capacity];
        freePool.clear();
        for (int i = 0; i < capacity; i++) {
            pool[i] = new PooledTexture(this);
            freePool.add(pool[i]);
        }
    }

    public synchronized void mallocLazyLoadPool(int capacity) {
        daoPool = new DynamicAssetObject[capacity];
        daoFreePool.clear();
        for (int i = 0; i < capacity; i++) {
            daoPool[i] = new DynamicAssetObject();
            daoFreePool.add(daoPool[i]);
        }
    }

    private PooledTexture getFreeTexture() {
        PooledTexture tex = freePool.poll();
        if (tex == null) {
            if (Core.get().isSafeRuntime()) {
                for (int i = 5; i < 50; i++) {
                    freePool.add(new PooledTexture(this));
                }
                Fw.Helper.getConsoleToBaseInstance(instance).addLog(Console.LogType.SAFE_RUNTIME,
                        "auto allocated TexturePool in AssetManager.");
                tex = freePool.poll();
            } else {
                throw new IllegalStateException("Out of texture pool! Increase pool size with mallocTexturePool().");
            }
        }
        tex.setInUse(true);
        return tex;
    }

    private DynamicAssetObject getFreeDao() {
        DynamicAssetObject dao = daoFreePool.poll();
        if (dao == null) {
            if (Core.get().isSafeRuntime()) {
                for (int i = 5; i < 50; i++) {
                    daoFreePool.add(new DynamicAssetObject());
                }
                Fw.Helper.getConsoleToBaseInstance(instance).addLog(Console.LogType.SAFE_RUNTIME,
                        "auto allocated DynamicAssetObjectPool in AssetManager.");
                dao = daoFreePool.poll();
            } else {
                throw new IllegalStateException("Out of DynamicAssetObject pool! Increase pool size with malloc().");
            }
        }
        return dao;
    }

    public synchronized Texture loadTexture(LoadMode mode, String assetKey, InputStream is, String eventKey) {
        if (is == null && !Core.get().isSafeRuntime()) {
            throw new IllegalArgumentException("InputStream cannot be null for texture assetKey: " + assetKey);
        }
        if (textureActiveMap.containsKey(assetKey) || pendingObjects.containsKey(assetKey)) {
            return textureActiveMap.get(assetKey);
        }

        PooledTexture texture = getFreeTexture();
        texture.init(assetKey, is);

        if (mode == LoadMode.SYNC) {
            try {
                texture.loadData();
                textureActiveMap.put(assetKey, texture);
            } catch (Exception e) {
                texture.close();
                freePool.offer(texture);
                throw new RuntimeException("SYNC loading fail: " + assetKey, e);
            }
        } else if (mode == LoadMode.LAZY) {
            DynamicAssetObject dao;
            try {
                dao = getFreeDao();
            } catch (RuntimeException e) {
                texture.close();
                freePool.offer(texture);
                throw e;
            }
            dao.init(() -> {
                try {
                    texture.loadData();
                    synchronized (AssetManager.this) {
                        if (!dao.isCancelled()) {
                            textureActiveMap.put(assetKey, texture);
                        }
                    }
                } catch (Exception e) {
                    synchronized (AssetManager.this) {
                        texture.close();
                        freePool.offer(texture);
                    }
                    throw new RuntimeException("LAZY loading fail: " + assetKey, e);
                } finally {
                    synchronized (AssetManager.this) {
                        cleanEmptyPendingEvent(eventKey, dao);
                    }
                }
            }, () -> {
                synchronized (AssetManager.this) {
                    if (dao.isCancelled() && texture.isInUse()) {
                        texture.close();
                        freePool.offer(texture);
                    }
                    recycleTask(assetKey, dao);
                }
            });
            pendingObjects.put(assetKey, dao);
            pendingEvents.computeIfAbsent(eventKey, k -> new CopyOnWriteArrayList<>()).add(dao);
        }

        return texture;
    }

    public synchronized SoundAsset loadSound(LoadMode mode, String assetKey, InputStream is, String eventKey) {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null for sound assetKey: " + assetKey);
        }
        if (soundActiveMap.containsKey(assetKey) || pendingObjects.containsKey(assetKey)) {
            return soundActiveMap.get(assetKey);
        }

        if (!InternalSoundModule.isInitialized()) {
            InternalSoundModule.init();
        }

        if (mode == LoadMode.SYNC) {
            try {
                SoundAsset sound = InternalSoundModule.loadSound(is);
                if (sound == null) {
                    throw new RuntimeException("Failed to load sound instance: " + assetKey);
                }
                soundActiveMap.put(assetKey, sound);
                return sound;
            } catch (Exception e) {
                throw new RuntimeException("SYNC sound loading fail: " + assetKey, e);
            }
        } else if (mode == LoadMode.LAZY) {
            DynamicAssetObject dao = getFreeDao();
            dao.init(() -> {
                SoundAsset sound = null;
                try {
                    sound = InternalSoundModule.loadSound(is);
                    if (sound == null) {
                        throw new RuntimeException("Failed to load sound instance: " + assetKey);
                    }
                    synchronized (AssetManager.this) {
                        if (dao.isCancelled()) {
                            sound.stop();
                            sound.free();
                        } else {
                            soundActiveMap.put(assetKey, sound);
                        }
                    }
                } catch (Exception e) {
                    if (sound != null) {
                        sound.stop();
                        sound.free();
                    }
                    throw new RuntimeException("LAZY sound loading fail: " + assetKey, e);
                } finally {
                    synchronized (AssetManager.this) {
                        cleanEmptyPendingEvent(eventKey, dao);
                    }
                }
            }, () -> recycleTask(assetKey, dao));
            pendingObjects.put(assetKey, dao);
            pendingEvents.computeIfAbsent(eventKey, k -> new CopyOnWriteArrayList<>()).add(dao);
        }

        return null;
    }

    public synchronized MusicAsset loadMusic(LoadMode mode, MusicType type, String assetKey, InputStream is, String eventKey) {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null for music assetKey: " + assetKey);
        }
        if (musicActiveMap.containsKey(assetKey) || pendingObjects.containsKey(assetKey)) {
            return musicActiveMap.get(assetKey);
        }

        if (!InternalSoundModule.isInitialized()) {
            InternalSoundModule.init();
        }

        if (mode == LoadMode.SYNC) {
            try {
                MusicAsset music = createMusicInstance(type, is);
                if (music == null) {
                    throw new RuntimeException("Failed to load music instance: " + assetKey);
                }
                musicActiveMap.put(assetKey, music);
                return music;
            } catch (Exception e) {
                throw new RuntimeException("SYNC music loading fail: " + assetKey, e);
            }
        } else if (mode == LoadMode.LAZY) {
            DynamicAssetObject dao = getFreeDao();
            dao.init(() -> {
                MusicAsset music = null;
                try {
                    music = createMusicInstance(type, is);
                    if (music == null) {
                        throw new RuntimeException("Failed to load music instance: " + assetKey);
                    }
                    synchronized (AssetManager.this) {
                        if (dao.isCancelled()) {
                            music.stop();
                            music.free();
                        } else {
                            musicActiveMap.put(assetKey, music);
                        }
                    }
                } catch (Exception e) {
                    if (music != null) {
                        music.stop();
                        music.free();
                    }
                    throw new RuntimeException("LAZY music loading fail: " + assetKey, e);
                } finally {
                    synchronized (AssetManager.this) {
                        cleanEmptyPendingEvent(eventKey, dao);
                    }
                }
            }, () -> recycleTask(assetKey, dao));
            pendingObjects.put(assetKey, dao);
            pendingEvents.computeIfAbsent(eventKey, k -> new CopyOnWriteArrayList<>()).add(dao);
        }

        return null;
    }

    private void cleanEmptyPendingEvent(String eventKey, DynamicAssetObject dao) {
        List<DynamicAssetObject> list = pendingEvents.get(eventKey);
        if (list != null) {
            list.remove(dao);
            if (list.isEmpty()) {
                pendingEvents.remove(eventKey, list);
            }
        }
    }

    private MusicAsset createMusicInstance(MusicType type, InputStream is) {
        return switch (type) {
            case MEM_MUSIC -> InternalSoundModule.loadMusic(is, false);
            case STREAM_MUSIC -> InternalSoundModule.loadMusic(is, true);
        };
    }

    public synchronized void event(String eventKey) {
        List<DynamicAssetObject> tasks = pendingEvents.remove(eventKey);
        if (tasks != null) {
            for (DynamicAssetObject task : tasks) {
                task.launch();
            }
        }
    }

    public Texture getTexture(String assetKey) { return textureActiveMap.get(assetKey); }

    public Sound getSound(String assetKey) { return soundActiveMap.get(assetKey); }

    public Music getMusic(String assetKey) { return musicActiveMap.get(assetKey); }

    private synchronized void recycleTask(String assetKey, DynamicAssetObject dao) {
        pendingObjects.remove(assetKey, dao);
        dao.reset();
        daoFreePool.offer(dao);
    }

    private boolean cancelPending(String assetKey) {
        DynamicAssetObject dao = pendingObjects.remove(assetKey);
        if (dao == null) return false;

        // pendingEvents에서 dao를 제거하고, 비어버린 리스트 엔트리는 맵에서 완전히 제거
        pendingEvents.entrySet().removeIf(entry -> {
            entry.getValue().remove(dao);
            return entry.getValue().isEmpty();
        });

        dao.cancel();
        if (dao.isStarted()) return true;

        dao.reset();
        daoFreePool.offer(dao);
        return false;
    }

    public synchronized void free(AssetType assetType, String assetKey) {
        boolean loading = cancelPending(assetKey);
        switch (assetType) {
            case TEXTURE -> {
                PooledTexture tex = textureActiveMap.remove(assetKey);
                if (tex != null) {
                    tex.close();
                    freePool.offer(tex);
                } else if (!loading && pool != null) {
                    for (PooledTexture t : pool) {
                        if (t != null && t.isInUse() && assetKey.equals(t.getAssetKey())) {
                            t.close();
                            freePool.offer(t);
                            break;
                        }
                    }
                }
            }
            case SOUND -> {
                SoundAsset sound = soundActiveMap.remove(assetKey);
                if (sound != null) {
                    sound.stop();
                    sound.free();
                }
            }
            case MUSIC -> {
                MusicAsset music = musicActiveMap.remove(assetKey);
                if (music != null) {
                    music.stop();
                    music.free();
                }
            }
        }
    }

    public synchronized void disposeAll() {
        Set<PooledTexture> loadingTextures = new HashSet<>();
        if (pool != null) {
            for (PooledTexture tex : pool) {
                if (tex != null) {
                    String assetKey = tex.getAssetKey();
                    DynamicAssetObject dao = assetKey == null ? null : pendingObjects.get(assetKey);
                    if (dao != null && dao.isStarted()) loadingTextures.add(tex);
                }
            }
        }
        textureActiveMap.clear();
        for (String assetKey : new ArrayList<>(pendingObjects.keySet())) {
            cancelPending(assetKey);
        }
        pendingEvents.clear();

        for (SoundAsset sound : soundActiveMap.values()) {
            if (sound != null) {
                sound.stop();
                sound.free();
            }
        }
        soundActiveMap.clear();

        for (MusicAsset sound : musicActiveMap.values()) {
            if (sound != null) {
                sound.stop();
                sound.free();
            }
        }
        musicActiveMap.clear();

        if (pool != null) {
            for (PooledTexture tex : pool) {
                if (tex != null && !loadingTextures.contains(tex)) tex.close();
            }
        }
        freePool.clear();
        if (pool != null) {
            for (PooledTexture tex : pool) {
                if (tex != null && !loadingTextures.contains(tex)) freePool.offer(tex);
            }
        }

        if (daoPool != null) {
            for (DynamicAssetObject dao : daoPool) {
                if (dao != null && !dao.isStarted()) dao.reset();
            }
        }
        daoFreePool.clear();
        if (daoPool != null) {
            for (DynamicAssetObject dao : daoPool) {
                if (dao != null && !dao.isStarted()) daoFreePool.offer(dao);
            }
        }

        InternalSoundModule.shutdown();
    }

    public static class SoundAPI {
        public static void setGlobalVolume(double volume) {InternalSoundModule.setGlobalVolume(volume);}
        public static double getGlobalVolume() {return InternalSoundModule.getGlobalVolume();}
        public static boolean isInitialized() {return InternalSoundModule.isInitialized();}
    }
}