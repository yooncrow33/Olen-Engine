package com.fw.main;

import com.fw.main.utils.platform.system.asset.BootTextureProxy;
import com.fw.main.utils.platform.system.asset.Music;
import com.fw.main.utils.platform.system.asset.Sound;
import com.fw.main.utils.platform.system.asset.Texture;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.MusicAsset;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.SoundAsset;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssetInit {
    final Map<String, InputStream> textureAssets = new LinkedHashMap<>();
    final Map<String, InputStream> soundAssets = new LinkedHashMap<>();
    final Map<String, InputStream> musicAssets = new LinkedHashMap<>();

    final Map<String, BootTextureProxy> textureProxies = new LinkedHashMap<>();
    final Map<String, BootSoundProxy> soundProxies = new LinkedHashMap<>();
    final Map<String, BootMusicProxy> musicProxies = new LinkedHashMap<>();

    public Texture registerBootTexture(String key, InputStream is) {
        if (key == null) throw new IllegalArgumentException("Key must not be null.");
        textureAssets.put(key, is);

        BootTextureProxy proxy = new BootTextureProxy(base.assetManager, key);
        textureProxies.put(key, proxy);
        return proxy;
    }

    final Base base;

    public AssetInit(Base base) {
        this.base = base;
    }

    public SoundAsset registerBootSound(String key, InputStream is) {
        if (key == null || is == null) throw new IllegalArgumentException("Key and InputStream must not be null.");

        soundAssets.put(key, is);

        BootSoundProxy proxy = new BootSoundProxy();
        soundProxies.put(key, proxy);
        return proxy;
    }

    public MusicAsset registerBootMusic(String key, InputStream is) {
        if (key == null || is == null) throw new IllegalArgumentException("Key and InputStream must not be null.");
        musicAssets.put(key, is);

        BootMusicProxy proxy = new BootMusicProxy();
        musicProxies.put(key, proxy);
        return proxy;
    }


    public static class BootSoundProxy extends SoundAsset {
        private volatile SoundAsset target;

        public void setTarget(SoundAsset target) {
            this.target = target;
        }

        @Override
        public void play() {
            Sound s = this.target;
            if (s != null) s.play();
        }

        @Override
        public void play(double volume) {
            Sound s = this.target;
            if (s != null) s.play(volume);
        }

        @Override
        public void play(double volume, double pan) {
            Sound s = this.target;
            if (s != null) s.play(volume, pan);
        }

        @Override
        public void stop() {
            Sound s = this.target;
            if (s != null) s.stop();
        }

        @Override
        public void free() {
            SoundAsset s = this.target;
            if (s != null) s.free();
        }
    }

    public static class BootMusicProxy extends MusicAsset {
        private volatile MusicAsset target;

        public void setTarget(MusicAsset target) {
            this.target = target;
        }

        @Override
        public void play(boolean loop) {
            Music m = this.target;
            if (m != null) m.play(loop);
        }

        @Override
        public void play(boolean loop, double volume) {
            Music m = this.target;
            if (m != null) m.play(loop, volume);
        }

        @Override
        public void play(boolean loop, double volume, double pan) {
            Music m = this.target;
            if (m != null) m.play(loop, volume, pan);
        }

        @Override
        public void stop() {
            Music m = this.target;
            if (m != null) m.stop();
        }

        @Override
        public void pause() {
            Music m = this.target;
            if (m != null) m.pause();
        }

        @Override
        public void resume() {
            Music m = this.target;
            if (m != null) m.resume();
        }

        @Override
        public void rewind() {
            Music m = this.target;
            if (m != null) m.rewind();
        }

        @Override
        public void rewindToLoopPosition() {
            Music m = this.target;
            if (m != null) m.rewindToLoopPosition();
        }

        @Override
        public boolean playing() {
            Music m = this.target;
            return m != null && m.playing();
        }

        @Override
        public boolean done() {
            Music m = this.target;
            return m == null || m.done();
        }

        @Override
        public boolean loop() {
            Music m = this.target;
            return m != null && m.loop();
        }

        @Override
        public void setLoop(boolean loop) {
            Music m = this.target;
            if (m != null) m.setLoop(loop);
        }

        @Override
        public int getLoopPositionByFrame() {
            Music m = this.target;
            return m != null ? m.getLoopPositionByFrame() : 0;
        }

        @Override
        public double getLoopPositionBySeconds() {
            Music m = this.target;
            return m != null ? m.getLoopPositionBySeconds() : 0.0;
        }

        @Override
        public void setLoopPositionByFrame(int frameIndex) {
            Music m = this.target;
            if (m != null) m.setLoopPositionByFrame(frameIndex);
        }

        @Override
        public void setLoopPositionBySeconds(double seconds) {
            Music m = this.target;
            if (m != null) m.setLoopPositionBySeconds(seconds);
        }

        @Override
        public double getVolume() {
            Music m = this.target;
            return m != null ? m.getVolume() : 0.0;
        }

        @Override
        public void setVolume(double volume) {
            Music m = this.target;
            if (m != null) m.setVolume(volume);
        }

        @Override
        public double getPan() {
            Music m = this.target;
            return m != null ? m.getPan() : 0.0;
        }

        @Override
        public void setPan(double pan) {
            Music m = this.target;
            if (m != null) m.setPan(pan);
        }

        @Override
        public void free() {
            MusicAsset m = this.target;
            if (m != null) m.free();
        }
    }
}