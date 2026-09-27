package com.fw.main.utils.platform.system.asset;

import com.fw.internal.utils.DynamicAsset;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DynamicAssetObject {
    private static final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AssetLoadThread");
        t.setDaemon(true);
        return t;
    });

    public static void submitTask(Runnable task) {
        loadExecutor.submit(task);
    }

    private final AtomicBoolean loadStart = new AtomicBoolean(false);
    private final AtomicBoolean loadEnd = new AtomicBoolean(false);
    private final AtomicBoolean loadError = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private DynamicAsset internalInterface;
    private Runnable onComplete;

    public DynamicAssetObject() {}

    public void init(DynamicAsset dynamicAsset) {
        init(dynamicAsset, null);
    }

    public void init(DynamicAsset dynamicAsset, Runnable onComplete) {
        this.internalInterface = dynamicAsset;
        this.onComplete = onComplete;
        this.loadStart.set(false);
        this.loadEnd.set(false);
        this.loadError.set(false);
        this.cancelled.set(false);
    }

    public void launch() {
        if (cancelled.get()) return;
        if (!loadStart.compareAndSet(false, true)) return;

        loadExecutor.submit(() -> {
            try {
                if (!cancelled.get() && internalInterface != null) {
                    internalInterface.load();
                }
            } catch (Exception e) {
                loadError.set(true);
                System.err.println("Asset Load Error: " + e.getMessage());
            } finally {
                loadEnd.set(true);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
    public boolean isStarted() { return loadStart.get(); }

    public boolean isLoaded() {
        return loadEnd.get() && !loadError.get();
    }

    public boolean isError() {
        return loadError.get();
    }

    public void reset() {
        this.internalInterface = null;
        this.loadStart.set(false);
        this.loadEnd.set(false);
        this.loadError.set(false);
        this.cancelled.set(false);
        this.onComplete = null;
    }
}
