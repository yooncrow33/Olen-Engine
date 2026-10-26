package com.fw.main.utils.platform.system.asset;

import com.fw.internal.utils.DynamicAsset;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private volatile CountDownLatch completionLatch = new CountDownLatch(1);
    private volatile Throwable loadException;   // ← 추가
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
        this.loadException = null;
        this.completionLatch = new CountDownLatch(1);
    }

    public void launch() {
        if (cancelled.get()) {
            completionLatch.countDown();
            return;
        }
        if (!loadStart.compareAndSet(false, true)) return;

        final CountDownLatch latch = this.completionLatch;
        final DynamicAsset targetAsset = this.internalInterface;
        final Runnable callback = this.onComplete;

        loadExecutor.submit(() -> {
            try {
                if (!cancelled.get() && targetAsset != null) {
                    targetAsset.load();
                }
            } catch (Exception e) {
                loadError.set(true);
                loadException = e;   // ← 추가
                System.err.println("Asset Load Error: " + e.getMessage());
            } finally {
                loadEnd.set(true);
                try {
                    if (callback != null) callback.run();
                } finally {
                    latch.countDown();
                }
            }
        });
    }

    public void awaitCompletion() throws InterruptedException {
        CountDownLatch latch = this.completionLatch;
        if (latch != null) latch.await();
    }

    /** 신규: await 후 로드 중 발생한 예외를 다시 던진다. */
    public void awaitCompletionOrThrow() throws Exception {
        CountDownLatch latch = this.completionLatch;
        if (latch != null) latch.await();
        Throwable t = this.loadException;
        if (t != null) {
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = this.completionLatch;
        return latch == null || latch.await(timeout, unit);
    }

    public void cancel() {
        cancelled.set(true);
        CountDownLatch latch = this.completionLatch;
        if (latch != null && !loadStart.get()) {
            latch.countDown();
        }
    }

    public boolean isCancelled() { return cancelled.get(); }
    public boolean isStarted() { return loadStart.get(); }
    public boolean isFinished() { return loadEnd.get() || cancelled.get(); }
    public boolean isLoaded() { return loadEnd.get() && !loadError.get(); }
    public boolean isError() { return loadError.get(); }
    public Throwable getLoadException() { return loadException; }   // ← 추가

    public void reset() {
        CountDownLatch latch = this.completionLatch;
        if (latch != null) latch.countDown();
        this.internalInterface = null;
        this.loadStart.set(false);
        this.loadEnd.set(false);
        this.loadError.set(false);
        this.cancelled.set(false);
        this.loadException = null;
        this.onComplete = null;
    }
}