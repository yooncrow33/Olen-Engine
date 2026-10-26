package com.fw.main;

import com.fw.internal.sys.base.view.AccessConsole;
import com.fw.internal.sys.base.view.IFrameSize;
import com.fw.internal.sys.base.view.ViewMetrics;
import com.fw.internal.sys.input.MouseAtBase;
import com.fw.internal.sys.operator.OperatorManager;
import com.fw.internal.utils.DynamicAsset;
import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.api.io.Io;
import com.fw.main.api.sys.ConsoleCMD;
import com.fw.main.utils.graphics.RU;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.input.KeyBindingBase;
import com.fw.main.utils.input.korean.TextModule;
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.input.mouse.MouseInterface;
import com.fw.main.utils.platform.system.asset.*;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.MusicAsset;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.SoundAsset;
import com.fw.main.utils.platform.system.console.Console;
import com.fw.main.utils.platform.system.console.autoComplete.AutoCompleteManager;
import com.fw.main.utils.platform.system.console.autoComplete.ConsoleLabel;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
import com.fw.main.utils.platform.system.scene.LoadingRenderer;
import com.fw.main.utils.platform.system.scene.Scene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public abstract class Base extends Canvas implements IFrameSize, PerformanceRecorder.BaseWorkTimeProvider, AccessConsole {
    private static final long RESIZE_SETTLE_NANOS = 150_000_000L;

    PerformanceRecorder.CaptureMode captureMode = PerformanceRecorder.CaptureMode.DO_NOT;
    public static String version = "PRE 0.3.0";
    public JFrame frame = new JFrame("Olen Engine");

    private boolean fullScreen = false;
    private boolean changeResolution = false;
    private int targetResWidth = -1;
    private int targetResHeight = -1;
    public int WINDOW_WIDTH;
    public int WINDOW_HEIGHT;
    private int targetBitDepth = DisplayMode.BIT_DEPTH_MULTI;
    private int targetRefreshRate = DisplayMode.REFRESH_RATE_UNKNOWN;
    private DisplayMode originalDisplayMode = null;
    private GraphicsDevice graphicsDevice = null;

    public enum ThreadMode {
        SINGLE,
        @Deprecated
        DUAL
    }

    public enum DrawMode {
        DIRECT, BUFFER
    }

    public enum TransitionPhase {
        NONE,
        TRANSITION_FADE_OUT,
        TRANSITION_LOADING,
        TRANSITION_FADE_IN
    }

    private final ReentrantReadWriteLock sceneLock = new ReentrantReadWriteLock();

    // Per-thread error suppression state
    private static class ErrorTracker {
        final Set<Integer> prevFrameErrors = new HashSet<>();
        final Set<Integer> currentFrameErrors = new HashSet<>();
        final Map<Integer, Integer> suppressedCounts = new HashMap<>();
        final Map<Integer, String> errorTitles = new HashMap<>();
    }

    private final ThreadLocal<ErrorTracker> errorTracker = ThreadLocal.withInitial(ErrorTracker::new);

    private DrawMode drawMode = DrawMode.DIRECT;
    private int bufferWidth = 1920;
    private int bufferHeight = 1080;
    private VolatileImage offscreenBuffer;

    private ThreadMode threadMode = ThreadMode.SINGLE;
    public ThreadMode getThreadMode() { return threadMode; }
    private Thread mainLoopThread;
    private Thread logicThread;
    private Thread renderThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean isExiting = new AtomicBoolean(false);
    private volatile long renderPausedUntilNanos = 0L;
    private RenderingOption renderingOption = RenderingOption.DEFAULT;
    private WaitMode waitMode = WaitMode.OS_SLEEP;
    private volatile boolean builderInitialized;

    private int fpsCounter = 0;
    private volatile int currentFps = 0;
    private volatile double currentFrameTimeMs = 0;
    private volatile double currentRenderWorkTimeMs = 0;
    private long lastFpsCheckTime = System.nanoTime();
    private long accumulatedRenderWorkNanos = 0L;

    private int cpuFpsCounter = 0;
    private volatile int currentCpuFps = 0;
    private volatile double currentCpuWorkTimeMs = 0.0;
    private long lastCpuFpsCheckTime = System.nanoTime();
    private long accumulatedCpuWorkNanos = 0L;

    private final Mouse mouse = new Mouse(this);
    public final Mouse getMouse() { return mouse; }
    private ViewMetrics viewMetrics;
    public final AssetManager assetManager = new AssetManager(this);
    @Deprecated
    @Internal
    public final Io io = new Io();
    final OperatorManager operatorManager = new OperatorManager();
    private final AssetInit assetInit = new AssetInit(this);
    private final BaseInit baseInit = new BaseInit(this);
    final AtomicBoolean initLoadEnd = new AtomicBoolean(false);
    final ArrayList<DynamicAsset> sysLoadStack = new ArrayList<>();

    private final Map<String, Scene> sceneRegistry = new ConcurrentHashMap<>();
    public final AtomicBoolean isChangeScene = new AtomicBoolean(false);
    public final AtomicBoolean isSceneLoading = new AtomicBoolean(false);
    public final AtomicBoolean isTransitioning = new AtomicBoolean(false);

    private volatile TransitionPhase transitionPhase = TransitionPhase.NONE;
    private volatile float fadeAlpha = 0.0f;
    private float fadeSpeed = Builder.DEFAULT_FADE_SPEED;
    private volatile LoadingRenderer loadingRenderer = null;

    public Scene getCurrentScene() { return currentScene; }
    volatile Scene currentScene = null;
    volatile Scene pendingScene = null;

    private BufferStrategy bufferStrategy;
    private VolatileImage vramBuffer;
    private VolatileImage vramPass1;

    private static final int CRT_SLICES = 64;
    private static final float CURVE_X = 0.05f;
    private static final float CURVE_Y = 0.06f;
    private static final float CRT_MARGIN = 0.04f;

    private final int[] p1_sx1 = new int[CRT_SLICES];
    private final int[] p1_sx2 = new int[CRT_SLICES];
    private final int[] p1_dy1 = new int[CRT_SLICES];
    private final int[] p1_dy2 = new int[CRT_SLICES];
    private final float[] p2_sy1 = new float[CRT_SLICES];
    private final float[] p2_sy2 = new float[CRT_SLICES];
    private final float[] p2_dxMargin = new float[CRT_SLICES];

    private BufferedImage crtOverlay;
    private int crtCachedW = -1;
    private int crtCachedH = -1;
    private boolean crtPrecomputed = false;

    private boolean crtJitterEnabled = true;
    private float crtScanlineFlickerTimer = 0.0f;

    private static final Color[] CRT_PULSE_COLORS = new Color[256];
    static {
        for (int i = 0; i < 256; i++) {
            CRT_PULSE_COLORS[i] = new Color(0, 0, 0, i);
        }
    }

    private TextModule textModule;
    private volatile MouseAtBase mouseAtBase = null;

    private volatile PerformanceRecorder recorder;
    public void setRecoderPhase(String str) { recorder.setPhase(str); }
    private boolean closeWindowWithKillVM = true;

    private volatile ConsoleCMD consoleCMD = null;
    public ConsoleCMD getConsoleCMD() { return consoleCMD; }
    ErrorBoxManager errorBoxManager = new ErrorBoxManager();
    Console console = new Console(this);
    private Texture logo;
    private boolean useEngineCursor = false;

    private Cursor engineHardwareCursor;

    final Font loadingMessageFont = new Font(Font.MONOSPACED, Font.BOLD, 48);

    @FunctionalInterface
    public interface LoadAction {
        void execute() throws Exception;
    }

    public static class LoadTask {
        private final String description;
        private final LoadAction action;

        public LoadTask(String description, LoadAction action) {
            this.description = description;
            this.action = action;
        }

        public String getDescription() { return description; }
        public LoadAction getAction() { return action; }
    }

    private final ArrayList<LoadTask> loadTasks = new ArrayList<>();
    private volatile String currentLoadingMessage = "Initializing...";
    private volatile float loadingProgress = 0.0f;

    public Base(Builder builder) {
        if (builder != null) initializeBuilder(builder);
    }

    public synchronized void setBuilder(Builder builder) {
        if (builderInitialized) throw new IllegalStateException("builder is already initialized");
        initializeBuilder(java.util.Objects.requireNonNull(builder, "builder"));
    }

    public void setBuilderAndLaunch(Builder builder) {
        setBuilder(builder);
        launch();
    }

    public final boolean isBuilderInitialized() { return builderInitialized; }

    private void initializeBuilder(Builder builder) {
        if (!Core.isIsSetConfig()) {
            if (builder.coreConfig != null) Core.setConfig(builder.coreConfig.build());
            else if (builder.coreConfigValue != null) Core.setConfig(builder.coreConfigValue);
        }
        if (!Core.isIsSetConfig()) {
            throw new IllegalStateException("Set Core config with Base.Builder.setCoreConfig(...) or Core.setConfig(...)");
        }

        builder.loadSavedValues();

        if (Core.get().isUseKoreanModule()) {
            textModule = new TextModule(this);
        }

        if (!builder.title.equals("null")) {
            frame.setTitle(builder.title);
        }

        this.closeWindowWithKillVM = builder.closeWindowWithKillVM;
        this.renderingOption = builder.renderingOption;
        this.waitMode = builder.waitMode;
        this.crtJitterEnabled = builder.crtJitterEnabled;
        this.threadMode = builder.threadMode;

        this.fullScreen = builder.fullScreen;
        this.changeResolution = builder.changeResolution;
        this.targetResWidth = builder.targetResWidth;
        this.targetResHeight = builder.targetResHeight;
        this.targetBitDepth = builder.targetBitDepth;
        this.targetRefreshRate = builder.targetRefreshRate;
        this.WINDOW_WIDTH = builder.virtualScreenWidth;
        this.WINDOW_HEIGHT = builder.virtualScreenHeight;
        this.useEngineCursor = builder.useEngineCursor;

        this.drawMode = builder.drawMode;
        this.bufferWidth = builder.bufferWidth;
        this.bufferHeight = builder.bufferHeight;

        this.fadeSpeed = builder.fadeSpeed;
        this.loadingRenderer = builder.loadingRenderer;

        captureMode = builder.performanceRecorderOption;

        this.recorder = new PerformanceRecorder(this, this, builder.performanceRecorderOption);
        recorder.setPhase("launch");

        viewMetrics = new ViewMetrics(this, Core.get().isUseIntegerPhysicalScaling(), WINDOW_WIDTH, WINDOW_HEIGHT);

        if (builder.integerKey != null) { Fw.add(builder.integerKey, this); }
        if (builder.stringKey != null) { Fw.add(builder.stringKey, this); }

        sysLoadStack.add(() -> {
            if (!builder.consoleUse) {
                console.isNotUse();
            }
        });
        sysLoadStack.add(() -> {
            File assetFolder = new File(System.getProperty("user.home") + File.separator + "." + Core.get().projectName + File.separator + "asset");
            if (!assetFolder.exists()) {
                assetFolder.mkdirs();
            }
        });
        sysLoadStack.add(() -> setConsole(new ConsoleInit()));
        sysLoadStack.add(() -> setMouse(getMouse()));
        sysLoadStack.add(() -> {
            if (console != null) { io.addIoObject("quickputsystem", console.getQuickPutManager()); }
        });
        sysLoadStack.add(() -> {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                recorder.exit(builder.dumpPerformanceDataFileName);

                if (console != null) {
                    try {
                        String userHome = System.getProperty("user.home");
                        String projName = Core.get().projectName;
                        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                        File logFile = new File(userHome + File.separator + "." + projName + File.separator + "logs" + File.separator + "console_" + timestamp + ".txt");

                        File parent = logFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }

                        console.exportLogsToTxt(logFile);
                    } catch (Exception e) {
                        System.err.println("Failed to execute console shutdown dump: " + e.getMessage());
                    }
                }
            }));
        });
        builder.saveValues();
        builderInitialized = true;
    }

    public enum WaitMode {
        OS_SLEEP, HYBRID, BUSY_WAIT
    }

    public static class Builder {
        public static final float DEFAULT_FADE_SPEED = 2.5f;

        Config.Builder coreConfig;
        Config coreConfigValue;
        String stringKey;
        Integer integerKey;
        boolean consoleUse;
        PerformanceRecorder.CaptureMode performanceRecorderOption = PerformanceRecorder.CaptureMode.DO_NOT;
        String dumpPerformanceDataFileName;
        RenderingOption renderingOption = RenderingOption.DEFAULT;
        WaitMode waitMode = WaitMode.OS_SLEEP;
        ThreadMode threadMode = ThreadMode.SINGLE;
        boolean closeWindowWithKillVM = true;
        boolean crtJitterEnabled = true;
        String title = "null";
        int virtualScreenWidth = 1920;
        int virtualScreenHeight = 1080;
        boolean useEngineCursor = false;

        boolean fullScreen = false;
        boolean changeResolution = false;
        int targetResWidth = -1;
        int targetResHeight = -1;
        int targetBitDepth = DisplayMode.BIT_DEPTH_MULTI;
        int targetRefreshRate = DisplayMode.REFRESH_RATE_UNKNOWN;

        DrawMode drawMode = DrawMode.DIRECT;
        int bufferWidth = 1920;
        int bufferHeight = 1080;

        float fadeSpeed = DEFAULT_FADE_SPEED;
        LoadingRenderer loadingRenderer = null;

        public Builder setCoreConfig(Config.Builder config) {
            coreConfig = java.util.Objects.requireNonNull(config); coreConfigValue = null; return this;
        }
        public Builder setCoreConfig(Config config) {
            coreConfigValue = java.util.Objects.requireNonNull(config); coreConfig = null; return this;
        }

        public Builder setStringKey(String stringKey) {
            this.stringKey = stringKey;
            return this;
        }

        public Builder setIntegerKey(Integer integerKey) {
            this.integerKey = integerKey;
            return this;
        }

        public Builder setUseConsole(boolean b) {
            this.consoleUse = b;
            return this;
        }

        public Builder setRenderingOption(RenderingOption renderingOption) {
            this.renderingOption = renderingOption;
            return this;
        }

        public Builder setWaitMode(WaitMode waitMode) {
            this.waitMode = java.util.Objects.requireNonNull(waitMode);
            return this;
        }

        public Builder setThreadMode(ThreadMode threadMode) {
            this.threadMode = java.util.Objects.requireNonNull(threadMode);
            return this;
        }

        public Builder setPerformanceRecorderOption(PerformanceRecorder.CaptureMode performanceRecorderOption, String dumpPerformanceDataFileName) {
            this.performanceRecorderOption = performanceRecorderOption;
            this.dumpPerformanceDataFileName = dumpPerformanceDataFileName;
            return this;
        }

        public Builder setCloseWindowWithKillVM(boolean closeWindowWithKillVM) {
            this.closeWindowWithKillVM = closeWindowWithKillVM;
            return this;
        }

        public Builder setCrtJitterEnabled(boolean crtJitterEnabled) {
            this.crtJitterEnabled = crtJitterEnabled;
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setFullScreen(boolean fullScreen) {
            this.fullScreen = fullScreen;
            return this;
        }

        public Builder setDisplayResolution(int width, int height) {
            return setDisplayResolution(width, height, DisplayMode.BIT_DEPTH_MULTI, DisplayMode.REFRESH_RATE_UNKNOWN);
        }

        public Builder setVirtualScreenWidth(int virtualScreenWidth) {
            this.virtualScreenWidth = virtualScreenWidth;
            return this;
        }

        public Builder setVirtualScreenHeight(int virtualScreenHeight) {
            this.virtualScreenHeight = virtualScreenHeight;
            return this;
        }

        public Builder setUseEngineCursor(boolean useEngineCursor) {
            this.useEngineCursor = useEngineCursor;
            return this;
        }

        public Builder setDisplayResolution(int width, int height, int bitDepth, int refreshRate) {
            this.changeResolution = true;
            this.targetResWidth = width;
            this.targetResHeight = height;
            this.targetBitDepth = bitDepth;
            this.targetRefreshRate = refreshRate;
            return this;
        }

        public Builder setDrawMode(DrawMode drawMode) {
            this.drawMode = java.util.Objects.requireNonNull(drawMode);
            return this;
        }

        public Builder setBufferSize(int bufferWidth, int bufferHeight) {
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            return this;
        }

        public Builder setFadeSpeed(float fadeSpeed) {
            if (fadeSpeed <= 0.0f) throw new IllegalArgumentException("fadeSpeed must be > 0");
            this.fadeSpeed = fadeSpeed;
            return this;
        }

        public Builder setLoadingRenderer(LoadingRenderer loadingRenderer) {
            this.loadingRenderer = loadingRenderer;
            return this;
        }

        private void loadSavedValues() {
            consoleUse = savedBoolean("base.consoleUse", consoleUse);
            closeWindowWithKillVM = savedBoolean("base.closeWindowWithKillVM", closeWindowWithKillVM);
            crtJitterEnabled = savedBoolean("base.crtJitterEnabled", crtJitterEnabled);
            fullScreen = savedBoolean("base.fullScreen", fullScreen);
            changeResolution = savedBoolean("base.changeResolution", changeResolution);

            try {
                targetResWidth = Integer.parseInt(SettingFiles.read("base.targetResWidth", String.valueOf(targetResWidth)));
                targetResHeight = Integer.parseInt(SettingFiles.read("base.targetResHeight", String.valueOf(targetResHeight)));
                targetBitDepth = Integer.parseInt(SettingFiles.read("base.targetBitDepth", String.valueOf(targetBitDepth)));
                targetRefreshRate = Integer.parseInt(SettingFiles.read("base.targetRefreshRate", String.valueOf(targetRefreshRate)));
                virtualScreenHeight = Integer.parseInt(SettingFiles.read("base.virtualScreenHeight", String.valueOf(virtualScreenHeight)));
                virtualScreenWidth = Integer.parseInt(SettingFiles.read("base.virtualScreenWidth", String.valueOf(virtualScreenWidth)));
                useEngineCursor = savedBoolean("base.useEngineCursor", useEngineCursor);
                drawMode = DrawMode.valueOf(SettingFiles.read("base.drawMode", drawMode.name()));
            } catch (NumberFormatException ignored) {}

            try {
                renderingOption = RenderingOption.valueOf(SettingFiles.read("base.renderingOption", renderingOption.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                waitMode = WaitMode.valueOf(SettingFiles.read("base.waitMode", waitMode.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                threadMode = ThreadMode.valueOf(SettingFiles.read("base.threadMode", threadMode.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                performanceRecorderOption = PerformanceRecorder.CaptureMode.valueOf(
                        SettingFiles.read("base.performanceRecorderOption", performanceRecorderOption.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                bufferWidth = Integer.parseInt(SettingFiles.read("base.bufferWidth", String.valueOf(bufferWidth)));
                bufferHeight = Integer.parseInt(SettingFiles.read("base.bufferHeight", String.valueOf(bufferHeight)));
            } catch (IllegalArgumentException ignored) { }
            try {
                fadeSpeed = Float.parseFloat(SettingFiles.read("base.fadeSpeed", String.valueOf(fadeSpeed)));
            } catch (IllegalArgumentException ignored) { }
            dumpPerformanceDataFileName = SettingFiles.read("base.dumpPerformanceDataFileName", dumpPerformanceDataFileName);
            if ("".equals(dumpPerformanceDataFileName)) dumpPerformanceDataFileName = null;
        }

        private void saveValues() {
            SettingFiles.write("base.consoleUse", Boolean.toString(consoleUse));
            SettingFiles.write("base.renderingOption", renderingOption.name());
            SettingFiles.write("base.waitMode", waitMode.name());
            SettingFiles.write("base.threadMode", threadMode.name());
            SettingFiles.write("base.performanceRecorderOption", performanceRecorderOption.name());
            SettingFiles.write("base.dumpPerformanceDataFileName", dumpPerformanceDataFileName);
            SettingFiles.write("base.closeWindowWithKillVM", Boolean.toString(closeWindowWithKillVM));
            SettingFiles.write("base.crtJitterEnabled", Boolean.toString(crtJitterEnabled));
            SettingFiles.write("base.fullScreen", Boolean.toString(fullScreen));
            SettingFiles.write("base.changeResolution", Boolean.toString(changeResolution));
            SettingFiles.write("base.targetResWidth", Integer.toString(targetResWidth));
            SettingFiles.write("base.targetResHeight", Integer.toString(targetResHeight));
            SettingFiles.write("base.targetBitDepth", Integer.toString(targetBitDepth));
            SettingFiles.write("base.targetRefreshRate", Integer.toString(targetRefreshRate));
            SettingFiles.write("base.virtualScreenWidth", Integer.toString(virtualScreenWidth));
            SettingFiles.write("base.virtualScreenHeight", Integer.toString(virtualScreenHeight));
            SettingFiles.write("base.useEngineCursor", Boolean.toString(useEngineCursor));
            SettingFiles.write("base.drawMode", drawMode.name());
            SettingFiles.write("base.bufferWidth", Integer.toString(bufferWidth));
            SettingFiles.write("base.bufferHeight", Integer.toString(bufferHeight));
            SettingFiles.write("base.fadeSpeed", Float.toString(fadeSpeed));
        }

        private static boolean savedBoolean(String name, boolean fallback) {
            String value = SettingFiles.read(name, Boolean.toString(fallback));
            return "true".equalsIgnoreCase(value) ? true : "false".equalsIgnoreCase(value) ? false : fallback;
        }
    }

    protected static final class SettingFiles {
        public static String read(String name, String fallback) {
            return EngineSettings.read(Core.get().getSettingsProjectName(), name, fallback);
        }

        public static void write(String name, String value) {
            EngineSettings.write(Core.get().getSettingsProjectName(), name, value);
        }
    }

    public class Mouse {
        final Base base;
        private MouseInterface userMouseInterface;

        public Mouse(Base base) { this.base = base; }
        public int x() { return base.getMouseX(); }
        public int y() { return base.getMouseY(); }

        public void registerMouseInterface(MouseInterface mouseInterface) {
            this.userMouseInterface = mouseInterface;
        }

        MouseInterface getUserMouseInterface() {
            return userMouseInterface;
        }
    }

    public class ConsoleInit {
        public void registerConsoleCMD(ConsoleCMD CMD) {
            if (consoleCMD != null) {
                System.err.println("ConsoleCMD is already init!");
                return;
            }
            consoleCMD = CMD;
        }
        public AutoCompleteManager getAuto() { return console.getAuto(); }
    }

    public class BaseInit {
        Base base;
        Scene initScene;
        BaseInit(Base base) { this.base = base; }
        public Io getIo() { return base.io; }
        public OperatorManager getOperatorManager() { return operatorManager; }
        public AssetInit getAssetInit() { return assetInit; }
        public void initSound() { InternalSoundModule.init(); }

        public void setInitScene(Scene initScene) {
            if (initScene != null) {
                initScene.DO_NOT_ACCESS.setBase(base);
            }
            this.initScene = initScene;
        }

        public void setInitScene(String sceneKey) {
            Scene target = base.sceneRegistry.get(sceneKey);
            if (target == null) {
                throw new IllegalArgumentException("Scene key not found in registry: " + sceneKey);
            }
            this.initScene = target;
        }
    }

    public synchronized void registerScene(String key, Scene scene) {
        if (key == null || scene == null) {
            throw new IllegalArgumentException("Scene key and Scene instance must not be null.");
        }
        scene.DO_NOT_ACCESS.setBase(this);
        scene.DO_NOT_ACCESS.setName(key);
        sceneRegistry.put(key, scene);
    }

    public Scene getRegisteredScene(String key) {
        return sceneRegistry.get(key);
    }

    @Internal
    public record SceneKeyLabel(String key) implements ConsoleLabel {
        @Override
        public String getConsoleLabel() {
            return key;
        }
    }

    private final List<ConsoleLabel> sceneLabelsView = new AbstractList<ConsoleLabel>() {
        @Override
        public ConsoleLabel get(int index) {
            int i = 0;
            for (String key : sceneRegistry.keySet()) {
                if (i++ == index) return new SceneKeyLabel(key);
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            return sceneRegistry.size();
        }

        @Override
        public Iterator<ConsoleLabel> iterator() {
            Iterator<String> it = sceneRegistry.keySet().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public ConsoleLabel next() { return new SceneKeyLabel(it.next()); }
            };
        }
    };

    public List<ConsoleLabel> getSceneKeyLabels() {
        return sceneLabelsView;
    }

    public void setLoadingRenderer(LoadingRenderer loadingRenderer) {
        this.loadingRenderer = loadingRenderer;
    }

    public LoadingRenderer getLoadingRenderer() {
        return loadingRenderer;
    }

    public void setFadeSpeed(float fadeSpeed) {
        if (fadeSpeed <= 0.0f) throw new IllegalArgumentException("fadeSpeed must be > 0");
        this.fadeSpeed = fadeSpeed;
    }

    public float getFadeSpeed() {
        return fadeSpeed;
    }

    public boolean isTransitioning() {
        return isTransitioning.get();
    }

    public TransitionPhase getTransitionPhase() {
        return transitionPhase;
    }

    protected boolean shouldBlockSceneInput() {
        return !initLoadEnd.get() || isTransitioning.get() || isSceneLoading.get();
    }

    protected void onGlobalEscPress() {
        Scene s = currentScene;
        if (s != null && !shouldBlockSceneInput()) {
            s.onKeyEscPress();
        }
    }

    protected void onGlobalEscRelease() {
        Scene s = currentScene;
        if (s != null && !shouldBlockSceneInput()) {
            s.onKeyEscRelease();
        }
    }

    private void bindEngineKeyRouter() {
        new KeyBindingBase(this) {
            private void route(Consumer<Scene> action) {
                if (shouldBlockSceneInput()) return;
                Scene s = currentScene;
                if (s != null) {
                    action.accept(s);
                }
            }

            @Override protected void onKeyEscPress() { onGlobalEscPress(); }
            @Override protected void onKeyEscRelease() { onGlobalEscRelease(); }

            @Override protected void onKeyAPress() { route(Scene::onKeyAPress); }
            @Override protected void onKeyBPress() { route(Scene::onKeyBPress); }
            @Override protected void onKeyCPress() { route(Scene::onKeyCPress); }
            @Override protected void onKeyDPress() { route(Scene::onKeyDPress); }
            @Override protected void onKeyEPress() { route(Scene::onKeyEPress); }
            @Override protected void onKeyFPress() { route(Scene::onKeyFPress); }
            @Override protected void onKeyGPress() { route(Scene::onKeyGPress); }
            @Override protected void onKeyHPress() { route(Scene::onKeyHPress); }
            @Override protected void onKeyIPress() { route(Scene::onKeyIPress); }
            @Override protected void onKeyJPress() { route(Scene::onKeyJPress); }
            @Override protected void onKeyKPress() { route(Scene::onKeyKPress); }
            @Override protected void onKeyLPress() { route(Scene::onKeyLPress); }
            @Override protected void onKeyMPress() { route(Scene::onKeyMPress); }
            @Override protected void onKeyNPress() { route(Scene::onKeyNPress); }
            @Override protected void onKeyOPress() { route(Scene::onKeyOPress); }
            @Override protected void onKeyPPress() { route(Scene::onKeyPPress); }
            @Override protected void onKeyQPress() { route(Scene::onKeyQPress); }
            @Override protected void onKeyRPress() { route(Scene::onKeyRPress); }
            @Override protected void onKeySPress() { route(Scene::onKeySPress); }
            @Override protected void onKeyTPress() { route(Scene::onKeyTPress); }
            @Override protected void onKeyUPress() { route(Scene::onKeyUPress); }
            @Override protected void onKeyVPress() { route(Scene::onKeyVPress); }
            @Override protected void onKeyWPress() { route(Scene::onKeyWPress); }
            @Override protected void onKeyXPress() { route(Scene::onKeyXPress); }
            @Override protected void onKeyYPress() { route(Scene::onKeyYPress); }
            @Override protected void onKeyZPress() { route(Scene::onKeyZPress); }

            @Override protected void onKey0Press() { route(Scene::onKey0Press); }
            @Override protected void onKey1Press() { route(Scene::onKey1Press); }
            @Override protected void onKey2Press() { route(Scene::onKey2Press); }
            @Override protected void onKey3Press() { route(Scene::onKey3Press); }
            @Override protected void onKey4Press() { route(Scene::onKey4Press); }
            @Override protected void onKey5Press() { route(Scene::onKey5Press); }
            @Override protected void onKey6Press() { route(Scene::onKey6Press); }
            @Override protected void onKey7Press() { route(Scene::onKey7Press); }
            @Override protected void onKey8Press() { route(Scene::onKey8Press); }
            @Override protected void onKey9Press() { route(Scene::onKey9Press); }

            @Override protected void onKeyF1Press() { route(Scene::onKeyF1Press); }
            @Override protected void onKeyF2Press() { route(Scene::onKeyF2Press); }
            @Override protected void onKeyF3Press() { route(Scene::onKeyF3Press); }
            @Override protected void onKeyF4Press() { route(Scene::onKeyF4Press); }
            @Override protected void onKeyF5Press() { route(Scene::onKeyF5Press); }
            @Override protected void onKeyF6Press() { route(Scene::onKeyF6Press); }
            @Override protected void onKeyF7Press() { route(Scene::onKeyF7Press); }
            @Override protected void onKeyF8Press() { route(Scene::onKeyF8Press); }
            @Override protected void onKeyF9Press() { route(Scene::onKeyF9Press); }
            @Override protected void onKeyF10Press() { route(Scene::onKeyF10Press); }
            @Override protected void onKeyF11Press() { route(Scene::onKeyF11Press); }
            @Override protected void onKeyF12Press() { route(Scene::onKeyF12Press); }

            @Override protected void onKeyShiftPress() { route(Scene::onKeyShiftPress); }
            @Override protected void onKeyCtrlPress() { route(Scene::onKeyCtrlPress); }
            @Override protected void onKeyAltPress() { route(Scene::onKeyAltPress); }
            @Override protected void onKeySpacePress() { route(Scene::onKeySpacePress); }
            @Override protected void onKeyEnterPress() { route(Scene::onKeyEnterPress); }
            @Override protected void onKeyTabPress() { route(Scene::onKeyTabPress); }
            @Override protected void onKeyBackspacePress() { route(Scene::onKeyBackspacePress); }

            @Override protected void onKeyUpPress() { route(Scene::onKeyUpPress); }
            @Override protected void onKeyDownPress() { route(Scene::onKeyDownPress); }
            @Override protected void onKeyLeftPress() { route(Scene::onKeyLeftPress); }
            @Override protected void onKeyRightPress() { route(Scene::onKeyRightPress); }

            @Override protected void onKeyARelease() { route(Scene::onKeyARelease); }
            @Override protected void onKeyBRelease() { route(Scene::onKeyBRelease); }
            @Override protected void onKeyCRelease() { route(Scene::onKeyCRelease); }
            @Override protected void onKeyDRelease() { route(Scene::onKeyDRelease); }
            @Override protected void onKeyERelease() { route(Scene::onKeyERelease); }
            @Override protected void onKeyFRelease() { route(Scene::onKeyFRelease); }
            @Override protected void onKeyGRelease() { route(Scene::onKeyGRelease); }
            @Override protected void onKeyHRelease() { route(Scene::onKeyHRelease); }
            @Override protected void onKeyIRelease() { route(Scene::onKeyIRelease); }
            @Override protected void onKeyJRelease() { route(Scene::onKeyJRelease); }
            @Override protected void onKeyKRelease() { route(Scene::onKeyKRelease); }
            @Override protected void onKeyLRelease() { route(Scene::onKeyLRelease); }
            @Override protected void onKeyMRelease() { route(Scene::onKeyMRelease); }
            @Override protected void onKeyNRelease() { route(Scene::onKeyNRelease); }
            @Override protected void onKeyORelease() { route(Scene::onKeyORelease); }
            @Override protected void onKeyPRelease() { route(Scene::onKeyPRelease); }
            @Override protected void onKeyQRelease() { route(Scene::onKeyQRelease); }
            @Override protected void onKeyRRelease() { route(Scene::onKeyRRelease); }
            @Override protected void onKeySRelease() { route(Scene::onKeySRelease); }
            @Override protected void onKeyTRelease() { route(Scene::onKeyTRelease); }
            @Override protected void onKeyURelease() { route(Scene::onKeyURelease); }
            @Override protected void onKeyVRelease() { route(Scene::onKeyVRelease); }
            @Override protected void onKeyWRelease() { route(Scene::onKeyWRelease); }
            @Override protected void onKeyXRelease() { route(Scene::onKeyXRelease); }
            @Override protected void onKeyYRelease() { route(Scene::onKeyYRelease); }
            @Override protected void onKeyZRelease() { route(Scene::onKeyZRelease); }

            @Override protected void onKey0Release() { route(Scene::onKey0Release); }
            @Override protected void onKey1Release() { route(Scene::onKey1Release); }
            @Override protected void onKey2Release() { route(Scene::onKey2Release); }
            @Override protected void onKey3Release() { route(Scene::onKey3Release); }
            @Override protected void onKey4Release() { route(Scene::onKey4Release); }
            @Override protected void onKey5Release() { route(Scene::onKey5Release); }
            @Override protected void onKey6Release() { route(Scene::onKey6Release); }
            @Override protected void onKey7Release() { route(Scene::onKey7Release); }
            @Override protected void onKey8Release() { route(Scene::onKey8Release); }
            @Override protected void onKey9Release() { route(Scene::onKey9Release); }

            @Override protected void onKeyF1Release() { route(Scene::onKeyF1Release); }
            @Override protected void onKeyF2Release() { route(Scene::onKeyF2Release); }
            @Override protected void onKeyF3Release() { route(Scene::onKeyF3Release); }
            @Override protected void onKeyF4Release() { route(Scene::onKeyF4Release); }
            @Override protected void onKeyF5Release() { route(Scene::onKeyF5Release); }
            @Override protected void onKeyF6Release() { route(Scene::onKeyF6Release); }
            @Override protected void onKeyF7Release() { route(Scene::onKeyF7Release); }
            @Override protected void onKeyF8Release() { route(Scene::onKeyF8Release); }
            @Override protected void onKeyF9Release() { route(Scene::onKeyF9Release); }
            @Override protected void onKeyF10Release() { route(Scene::onKeyF10Release); }
            @Override protected void onKeyF11Release() { route(Scene::onKeyF11Release); }
            @Override protected void onKeyF12Release() { route(Scene::onKeyF12Release); }

            @Override protected void onKeyShiftRelease() { route(Scene::onKeyShiftRelease); }
            @Override protected void onKeyCtrlRelease() { route(Scene::onKeyCtrlRelease); }
            @Override protected void onKeyAltRelease() { route(Scene::onKeyAltRelease); }
            @Override protected void onKeySpaceRelease() { route(Scene::onKeySpaceRelease); }
            @Override protected void onKeyEnterRelease() { route(Scene::onKeyEnterRelease); }
            @Override protected void onKeyTabRelease() { route(Scene::onKeyTabRelease); }
            @Override protected void onKeyBackspaceRelease() { route(Scene::onKeyBackspaceRelease); }

            @Override protected void onKeyUpRelease() { route(Scene::onKeyUpRelease); }
            @Override protected void onKeyDownRelease() { route(Scene::onKeyDownRelease); }
            @Override protected void onKeyLeftRelease() { route(Scene::onKeyLeftRelease); }
            @Override protected void onKeyRightRelease() { route(Scene::onKeyRightRelease); }
        };
    }

    public void windowSetup() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setResizable(!fullScreen);

        if (fullScreen) {
            frame.setUndecorated(true);
        }

        this.setPreferredSize(new Dimension(Core.get().initWindowWidth, Core.get().getInitWindowHeight()));
        setFocusable(true);
        setIgnoreRepaint(true);

        frame.add(this);

        if (fullScreen) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            graphicsDevice = env.getDefaultScreenDevice();

            if (graphicsDevice.isFullScreenSupported()) {
                frame.pack();
                frame.setVisible(true);

                graphicsDevice.setFullScreenWindow(frame);
                originalDisplayMode = graphicsDevice.getDisplayMode();

                if (changeResolution && targetResWidth > 0 && targetResHeight > 0) {
                    if (graphicsDevice.isDisplayChangeSupported()) {
                        DisplayMode bestMode = findBestDisplayMode(graphicsDevice, targetResWidth, targetResHeight, targetBitDepth, targetRefreshRate);
                        if (bestMode != null) {
                            try {
                                graphicsDevice.setDisplayMode(bestMode);
                            } catch (Exception ex) {
                                System.err.println("Failed to switch display resolution: " + ex.getMessage());
                            }
                        } else {
                            System.err.println("Unsupported display mode: " + targetResWidth + "x" + targetResHeight);
                        }
                    } else {
                        System.err.println("Display resolution change is not supported in the current environment.");
                    }
                }
            } else {
                System.err.println("FSEM (Full-Screen Exclusive Mode) is not supported. Falling back to windowed mode.");
                frame.setUndecorated(false);
                frame.pack();
                frame.setVisible(true);
            }
        } else {
            frame.pack();
            frame.setVisible(true);
        }

        if (useEngineCursor) {
            try (InputStream is = InternalUtils.getEngineResourceStream("EngineCursor.png")) {
                if (is != null) {
                    BufferedImage cursorImg = javax.imageio.ImageIO.read(is);

                    int targetWidth = 32;
                    int targetHeight = 32;

                    Image scaled = cursorImg.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
                    BufferedImage resizedCursorImg = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = resizedCursorImg.createGraphics();
                    g2d.drawImage(scaled, 0, 0, null);
                    g2d.dispose();

                    engineHardwareCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                            resizedCursorImg, new Point(0, 0), "EngineHardwareCursor"
                    );
                    this.setCursor(engineHardwareCursor);
                    frame.setCursor(engineHardwareCursor);
                } else {
                    System.err.println("[Base] Failed to find EngineCursor.png");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        this.requestFocus();
        setBackground(Color.BLACK);
        viewMetrics.calculateViewMetrics();

        this.createBufferStrategy(2);
        this.bufferStrategy = this.getBufferStrategy();

        this.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (renderingOption != null && renderingOption.name().equals("CRT")) {
                    viewMetrics.updateVirtualMouseCrt(e.getX(), e.getY());
                } else {
                    viewMetrics.updateVirtualMouse(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                updateVirtual(e);
            }

            private void updateVirtual(MouseEvent e) {
                if (renderingOption != null && renderingOption.name().equals("CRT")) {
                    viewMetrics.updateVirtualMouseCrt(e.getX(), e.getY());
                } else {
                    viewMetrics.updateVirtualMouse(e.getX(), e.getY());
                }
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                viewMetrics.calculateViewMetrics();
                renderPausedUntilNanos = System.nanoTime() + RESIZE_SETTLE_NANOS;
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                viewMetrics.calculateViewMetrics();
                renderPausedUntilNanos = System.nanoTime() + RESIZE_SETTLE_NANOS;
            }
        });

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                viewMetrics.calculateViewMetrics();
                renderPausedUntilNanos = System.nanoTime() + RESIZE_SETTLE_NANOS;
            }
        });

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (closeWindowWithKillVM) {
                    exit(true);
                } else {
                    exit();
                }
            }
        });

        bindEngineKeyRouter();
    }

    private DisplayMode findBestDisplayMode(GraphicsDevice device, int width, int height, int bitDepth, int refreshRate) {
        DisplayMode[] modes = device.getDisplayModes();
        DisplayMode candidate = null;

        for (DisplayMode mode : modes) {
            if (mode.getWidth() == width && mode.getHeight() == height) {
                boolean bitMatch = (bitDepth == DisplayMode.BIT_DEPTH_MULTI || mode.getBitDepth() == DisplayMode.BIT_DEPTH_MULTI || mode.getBitDepth() == bitDepth);
                boolean rateMatch = (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN || mode.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN || mode.getRefreshRate() == refreshRate);

                if (bitMatch && rateMatch) {
                    return mode;
                }
                if (candidate == null && bitMatch) {
                    candidate = mode;
                }
            }
        }
        return candidate;
    }

    private void initInputEngine() {
        this.mouseAtBase = new MouseAtBase(this);
        this.mouseAtBase.registerInterface(new MouseInterface() {
            private void route(Consumer<MouseInterface> action) {
                if (shouldBlockSceneInput()) return;
                MouseInterface userHook = mouse.getUserMouseInterface();
                if (userHook != null) {
                    action.accept(userHook);
                }
                Scene s = currentScene;
                if (s != null) {
                    action.accept(s);
                }
            }

            @Override public void mouseClicked(FwMouseAPI e) { route(m -> m.mouseClicked(e)); }
            @Override public void mousePressed(FwMouseAPI e) { route(m -> m.mousePressed(e)); }
            @Override public void mouseReleased(FwMouseAPI e) { route(m -> m.mouseReleased(e)); }
            @Override public void mouseEntered(FwMouseAPI e) { route(m -> m.mouseEntered(e)); }
            @Override public void mouseExited(FwMouseAPI e) { route(m -> m.mouseExited(e)); }
            @Override public void mouseWheelMoved(FwMouseAPI e) { route(m -> m.mouseWheelMoved(e)); }
            @Override public void mouseDragged(FwMouseAPI e) { route(m -> m.mouseDragged(e)); }
            @Override public void mouseMoved(FwMouseAPI e) { route(m -> m.mouseMoved(e)); }
        });
        this.requestFocus();
    }

    private void appendScenePipelineTasks(List<LoadTask> targetQueue, Scene scene) {
        if (scene == null) return;

        targetQueue.add(new LoadTask("Initializing Scene: " + scene.name, () -> {
            scene.DO_NOT_ACCESS.setBase(this);
            scene.DO_NOT_ACCESS.prepareForLoad();
            scene.init(scene.DO_NOT_ACCESS.getSceneInit());

            List<LoadTask> sceneTasks = new ArrayList<>(scene.DO_NOT_ACCESS.getPendingLoadTasks());
            List<String> lazyEvents = new ArrayList<>(scene.DO_NOT_ACCESS.getBoundLazyEvents());

            for (LoadTask st : sceneTasks) {
                targetQueue.add(st);
            }

            for (String eventKey : lazyEvents) {
                targetQueue.add(new LoadTask("Launching Lazy Assets [" + eventKey + "]...", () -> {
                    List<DynamicAssetObject> daos = assetManager.launchAndSnapshotEventTasks(eventKey);
                    int totalDaos = daos.size();
                    for (int idx = 0; idx < totalDaos; idx++) {
                        final int dIdx = idx;
                        final DynamicAssetObject dao = daos.get(idx);
                        targetQueue.add(new LoadTask(
                                String.format("Awaiting Lazy [%s] %d/%d", eventKey, dIdx + 1, totalDaos),
                                dao::awaitCompletionOrThrow
                        ));
                    }
                }));
            }

            if (scene.canSave()) {
                targetQueue.add(new LoadTask("Loading Scene Data: " + scene.name, () -> {
                    scene.DO_NOT_ACCESS.load();
                }));
            }
        }));
    }

    private void startAsyncLoading() {
        loadTasks.clear();

        loadTasks.add(new LoadTask("Initializing Core System...", () -> {
            init(baseInit);
            onAssetInit(assetInit);

            int insertIdx = 1;
            for (DynamicAsset dynamicAsset : sysLoadStack) {
                loadTasks.add(insertIdx++, new LoadTask("Configuring System...", dynamicAsset::load));
            }

            for (Map.Entry<String, InputStream> entry : assetInit.textureAssets.entrySet()) {
                String key = entry.getKey();
                InputStream is = entry.getValue();
                loadTasks.add(insertIdx++, new LoadTask("Loading texture: " + key, () -> {
                    Texture real = assetManager.loadTexture(AssetManager.LoadMode.SYNC, key, is, null);
                    BootTextureProxy proxy = assetInit.textureProxies.get(key);
                    if (proxy != null) proxy.setTarget(real);
                }));
            }

            for (Map.Entry<String, InputStream> entry : assetInit.soundAssets.entrySet()) {
                String key = entry.getKey();
                InputStream is = entry.getValue();
                loadTasks.add(insertIdx++, new LoadTask("Loading sound: " + key, () -> {
                    SoundAsset real = assetManager.loadSound(AssetManager.LoadMode.SYNC, key, is, null);
                    AssetInit.BootSoundProxy proxy = assetInit.soundProxies.get(key);
                    if (proxy != null) proxy.setTarget(real);
                }));
            }

            for (Map.Entry<String, InputStream> entry : assetInit.musicAssets.entrySet()) {
                String key = entry.getKey();
                InputStream is = entry.getValue();
                loadTasks.add(insertIdx++, new LoadTask("Loading music: " + key, () -> {
                    MusicAsset real = assetManager.loadMusic(AssetManager.LoadMode.SYNC, AssetManager.MusicType.STREAM_MUSIC, key, is, null);
                    AssetInit.BootMusicProxy proxy = assetInit.musicProxies.get(key);
                    if (proxy != null) proxy.setTarget(real);
                }));
            }

            for (AssetInit.BootAtlasEntry bootAtlas : assetInit.bootAtlases) {
                loadTasks.add(insertIdx++, new LoadTask("Baking Boot Atlas: " + bootAtlas.name, () -> {
                    bootAtlas.atlas.DO_NOT_ACCESS.bake(
                            bootAtlas.binder.getTempTextures(),
                            bootAtlas.binder.getPendingSprites(),
                            bootAtlas.padding
                    );
                }));
            }

            loadTasks.add(insertIdx++, new LoadTask("Loading Game Data...", () -> {
                io.load.loadStart = true;
                io.load.load();
                io.load.loadEnd = true;
            }));

            Scene initialScene = baseInit.initScene;
            if (initialScene != null) {
                appendScenePipelineTasks(loadTasks, initialScene);
            }
        }));

        new Thread(() -> {
            for (int i = 0; i < loadTasks.size(); i++) {
                int total = loadTasks.size();
                LoadTask task = loadTasks.get(i);
                addLog(task.description);
                currentLoadingMessage = task.getDescription();
                loadingProgress = Math.max(loadingProgress, (float) i / Math.max(1, total));

                try {
                    task.getAction().execute();
                } catch (Throwable t) {
                    t.printStackTrace();
                    errorBoxManager.addError(
                            "Load Failed: " + t.getClass().getSimpleName(),
                            task.getDescription() + " (" + (t.getMessage() != null ? t.getMessage() : "null") + ")"
                    );
                }
            }

            sceneLock.writeLock().lock();
            try {
                currentScene = baseInit.initScene;
            } finally {
                isSceneLoading.set(false);
                fadeAlpha = 1.0f;
                transitionPhase = TransitionPhase.TRANSITION_FADE_IN;
                sceneLock.writeLock().unlock();
            }
            if (currentScene != null) {
                currentScene.DO_NOT_ACCESS.notifyEnter();
            }

            loadingProgress = 1.0f;
            currentLoadingMessage = "Complete!";
            initLoadEnd.set(true);
        }, "Async-Loader").start();
    }

    public void launch() {
        if (!builderInitialized) {
            System.err.println("Engine launch refused: call setBuilder(...) before launch().");
            return;
        }

        windowSetup();
        initInputEngine();
        setUp();

        if (Core.get().loadingScreenTexture != null) {
            logo = assetManager.loadTexture(AssetManager.LoadMode.SYNC, "engine_logo", Core.get().loadingScreenTexture, null);
        } else {
            logo = assetManager.loadTexture(AssetManager.LoadMode.SYNC, "engine_logo", InternalUtils.getEngineResourceStream("Olen.png"), null);
            assetInit.registerBootTexture("wrong", InternalUtils.getEngineResourceStream("WrongTexture.png"));
        }

        threadLaunch();
        startAsyncLoading();
    }

    public synchronized boolean changeScene(String sceneKey) {
        Scene target = sceneRegistry.get(sceneKey);
        if (target == null) {
            addErrorLog("Scene key not found in registry: " + sceneKey);
            return false;
        }
        return changeScene(target);
    }

    public synchronized boolean changeScene(Scene newScene) {
        if (newScene == null) {
            System.err.println("new scene is null!");
            return false;
        }
        if (newScene == currentScene || isTransitioning.get() || isSceneLoading.get() || isChangeScene.get()) {
            return false;
        }

        newScene.DO_NOT_ACCESS.setBase(this);
        this.pendingScene = newScene;
        this.isTransitioning.set(true);
        this.transitionPhase = TransitionPhase.TRANSITION_FADE_OUT;
        this.isChangeScene.set(true);
        return true;
    }

    private void updateSceneTransition(double dt) {
        TransitionPhase phase = this.transitionPhase;
        if (phase == TransitionPhase.NONE) return;

        if (phase == TransitionPhase.TRANSITION_FADE_OUT) {
            fadeAlpha += (float) (fadeSpeed * dt);
            if (fadeAlpha >= 1.0f) {
                fadeAlpha = 1.0f;
                transitionPhase = TransitionPhase.TRANSITION_LOADING;
                startSceneAsyncLoader();
            }
        } else if (phase == TransitionPhase.TRANSITION_FADE_IN) {
            fadeAlpha -= (float) (fadeSpeed * dt);
            if (fadeAlpha <= 0.0f) {
                fadeAlpha = 0.0f;
                transitionPhase = TransitionPhase.NONE;
                isTransitioning.set(false);
            }
        }
    }

    private void startSceneAsyncLoader() {
        if (!isChangeScene.compareAndSet(true, false)) return;

        final Scene nextScene = this.pendingScene;
        this.pendingScene = null;

        if (nextScene == null) {
            transitionPhase = TransitionPhase.TRANSITION_FADE_IN;
            return;
        }

        isSceneLoading.set(true);
        loadingProgress = 0.0f;
        currentLoadingMessage = "Preparing Scene: " + nextScene.name;

        sceneLock.writeLock().lock();
        try {
            if (currentScene != null) {
                setRecoderPhase("dispose scene: " + currentScene.name);
                currentScene.DO_NOT_ACCESS.dispose();
                currentScene = null;
            }
        } finally {
            sceneLock.writeLock().unlock();
        }

        new Thread(() -> {
            try {
                setRecoderPhase("init scene: " + nextScene.name);
                List<LoadTask> sceneQueue = new ArrayList<>();
                appendScenePipelineTasks(sceneQueue, nextScene);

                for (int i = 0; i < sceneQueue.size(); i++) {
                    int total = sceneQueue.size();
                    LoadTask task = sceneQueue.get(i);
                    addLog(task.getDescription());
                    currentLoadingMessage = task.getDescription();
                    loadingProgress = Math.max(loadingProgress, (float) i / Math.max(1, total));
                    task.getAction().execute();
                }

                loadingProgress = 1.0f;
                currentLoadingMessage = "Complete!";

                sceneLock.writeLock().lock();
                try {
                    currentScene = nextScene;
                } finally {
                    sceneLock.writeLock().unlock();
                }

                nextScene.DO_NOT_ACCESS.notifyEnter();

            } catch (Throwable t) {
                t.printStackTrace();
                errorBoxManager.addError(
                        "Scene Load Error: " + t.getClass().getSimpleName(),
                        nextScene.name + " (" + (t.getMessage() != null ? t.getMessage() : "null") + ")"
                );
                System.err.println("Scene loading failed: " + t.getMessage());
            } finally {
                isSceneLoading.set(false);
                transitionPhase = TransitionPhase.TRANSITION_FADE_IN;
            }
        }, "Async-Loader").start();
    }

    private void stepEngineUpdate(double deltaTime) {
        long cpuStartNanos = System.nanoTime();

        updateSceneTransition(deltaTime);

        sceneLock.readLock().lock();
        try {
            Scene activeScene = currentScene;
            if (activeScene != null && !isSceneLoading.get()) {
                activeScene.update(deltaTime);
            }
        } finally {
            sceneLock.readLock().unlock();
        }

        update(deltaTime);

        if (recorder != null) recorder.update();
        recordCpuPresentedFrame(System.nanoTime() - cpuStartNanos);
    }

    private void threadLaunch() {
        running.set(true);

        if (threadMode == ThreadMode.SINGLE) {
            addLog(InternalUtils.Time.getTimeFormate() + " / single-thread loop start");

            mainLoopThread = new Thread(() -> {
                long lastTime = System.nanoTime();
                long nextTick = lastTime;
                final double targetFps = 60.0;
                final long nsPerTick = (long) (1_000_000_000.0 / targetFps);

                while (running.get()) {
                    long now = System.nanoTime();
                    double deltaTime = (now - lastTime) / 1_000_000_000.0;
                    lastTime = now;

                    try {
                        if (initLoadEnd.get()) {
                            stepEngineUpdate(deltaTime);
                        }

                        renderLoop();
                    } catch (Throwable t) {
                        logThreadException(t);
                    } finally {
                        flushSuppressionNotice();
                    }

                    nextTick += nsPerTick;
                    if (System.nanoTime() - nextTick >= nsPerTick) nextTick = System.nanoTime();
                    waitForNextTick(nextTick);
                }
            });

            mainLoopThread.setName("mainLoop");
            mainLoopThread.start();

        } else {
            addLog(InternalUtils.Time.getTimeFormate() + " / logic thread start");

            logicThread = new Thread(() -> {
                long lastTime = System.nanoTime();
                long nextTick = lastTime;
                final double targetFps = 60.0;
                final long nsPerTick = (long) (1_000_000_000.0 / targetFps);

                while (running.get()) {
                    long now = System.nanoTime();
                    double deltaTime = (now - lastTime) / 1_000_000_000.0;
                    lastTime = now;

                    try {
                        if (initLoadEnd.get()) {
                            stepEngineUpdate(deltaTime);
                        }
                    } catch (Throwable t) {
                        logThreadException(t);
                    } finally {
                        flushSuppressionNotice();
                    }

                    nextTick += nsPerTick;
                    if (System.nanoTime() - nextTick >= nsPerTick) nextTick = System.nanoTime();
                    waitForNextTick(nextTick);
                }
            });

            logicThread.setName("logicLoop");
            logicThread.start();

            addLog(InternalUtils.Time.getTimeFormate() + " / render thread start");

            renderThread = new Thread(() -> {
                long lastTime = System.nanoTime();
                long nextTick = lastTime;
                final double targetFps = 60.0;
                final long nsPerTick = (long) (1_000_000_000.0 / targetFps);

                while (running.get()) {
                    long now = System.nanoTime();
                    lastTime = now;

                    try {
                        renderLoop();
                    } catch (Throwable t) {
                        logThreadException(t);
                    } finally {
                        flushSuppressionNotice();
                    }

                    nextTick += nsPerTick;
                    if (System.nanoTime() - nextTick >= nsPerTick) nextTick = System.nanoTime();
                    waitForNextTick(nextTick);
                }
            });

            renderThread.setName("renderLoop");
            renderThread.start();
        }
    }

    private void waitForNextTick(long deadlineNanos) {
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (waitMode == WaitMode.BUSY_WAIT) {
                if (remaining > 2_000_000) {
                    Thread.sleep((remaining - 2_000_000) / 1_000_000);
                }
                while (running.get() && deadlineNanos - System.nanoTime() > 0) {
                    Thread.yield();
                }
            } else if (waitMode == WaitMode.OS_SLEEP) {
                while (running.get() && remaining > 0) {
                    Thread.sleep(remaining / 1_000_000, (int) (remaining % 1_000_000));
                    remaining = deadlineNanos - System.nanoTime();
                }
            } else {
                if (remaining > 3_000_000) {
                    Thread.sleep((remaining - 3_000_000) / 1_000_000);
                }
                while (running.get() && (remaining = deadlineNanos - System.nanoTime()) > 1_000_000) {
                    LockSupport.parkNanos(remaining - 1_000_000);
                    if (Thread.interrupted()) throw new InterruptedException();
                }
                while (running.get() && deadlineNanos - System.nanoTime() > 0) {
                    Thread.onSpinWait();
                }
            }
        } catch (InterruptedException e) {
            running.set(false);
        }
    }

    private void precomputeCrtMath() {
        if (crtPrecomputed) return;

        for (int i = 0; i < CRT_SLICES; i++) {
            p1_sx1[i] = i * WINDOW_WIDTH / CRT_SLICES;
            p1_sx2[i] = (i + 1) * WINDOW_WIDTH / CRT_SLICES;

            float nx = ((i + 0.5f) / CRT_SLICES - 0.5f) * 2.0f;
            float yMargin = WINDOW_HEIGHT * 0.5f * (nx * nx * CURVE_Y);

            p1_dy1[i] = (int) Math.round(yMargin);
            p1_dy2[i] = (int) Math.round(WINDOW_HEIGHT - yMargin);
        }

        for (int i = 0; i < CRT_SLICES; i++) {
            p2_sy1[i] = (float) i / CRT_SLICES;
            p2_sy2[i] = (float) (i + 1) / CRT_SLICES;

            float ny = ((i + 0.5f) / CRT_SLICES - 0.5f) * 2.0f;
            p2_dxMargin[i] = 0.5f * (ny * ny * CURVE_X);
        }

        crtPrecomputed = true;
    }

    private void updateCrtOverlay(int drawW, int drawH) {
        if (crtCachedW == drawW && crtCachedH == drawH && crtOverlay != null) {
            return;
        }
        crtCachedW = drawW;
        crtCachedH = drawH;

        GraphicsConfiguration gc = getGraphicsConfiguration();
        crtOverlay = gc.createCompatibleImage(drawW, drawH, Transparency.TRANSLUCENT);
        Graphics2D g2 = crtOverlay.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        LinearGradientPaint glassGlare = new LinearGradientPaint(
                0, 0, drawW * 0.7f, drawH * 0.7f,
                new float[]{0.0f, 0.25f, 0.5f, 1.0f},
                new Color[]{
                        new Color(255, 255, 255, 18),
                        new Color(255, 255, 255, 6),
                        new Color(255, 255, 255, 0),
                        new Color(255, 255, 255, 0)
                }
        );
        g2.setPaint(glassGlare);
        g2.fillRect(0, 0, drawW, drawH);

        RadialGradientPaint softVignette = new RadialGradientPaint(
                new Point2D.Float(drawW / 2.0f, drawH / 2.0f),
                (float) Math.hypot(drawW / 2.0f, drawH / 2.0f) * 0.75f,
                new float[]{0.0f, 0.70f, 1.0f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 25),
                        new Color(0, 0, 0, 95)
                }
        );
        g2.setPaint(softVignette);
        g2.fillRect(0, 0, drawW, drawH);

        g2.setColor(new Color(0, 0, 0, 70));
        for (int y = 0; y < drawH; y += 4) {
            g2.fillRect(0, y, drawW, 2);
        }

        g2.setColor(new Color(0, 0, 0, 45));
        for (int x = 0; x < drawW; x += 3) {
            g2.fillRect(x, 0, 1, drawH);
        }

        int corner = (int) (Math.min(drawW, drawH) * 0.08f);
        RoundRectangle2D rect = new RoundRectangle2D.Float(0, 0, drawW, drawH, corner, corner);
        Area outside = new Area(new Rectangle(0, 0, drawW, drawH));
        outside.subtract(new Area(rect));
        g2.setColor(Color.BLACK);
        g2.fill(outside);

        g2.dispose();
    }

    private void renderLoop() {
        if (Thread.interrupted() || !running.get()) return;
        if (System.nanoTime() < renderPausedUntilNanos) return;

        BufferStrategy strategy = bufferStrategy;
        if (strategy == null || !isDisplayable()) return;

        int currentWidth = getWidth();
        int currentHeight = getHeight();
        if (currentWidth <= 0 || currentHeight <= 0) return;

        if (renderingOption != null && renderingOption.name().equals("CRT")) {
            GraphicsConfiguration gc = getGraphicsConfiguration();

            if (vramBuffer == null ||
                    vramBuffer.getWidth() != WINDOW_WIDTH ||
                    vramBuffer.getHeight() != WINDOW_HEIGHT ||
                    vramBuffer.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                vramBuffer = gc.createCompatibleVolatileImage(WINDOW_WIDTH, WINDOW_HEIGHT, Transparency.OPAQUE);
            }
            if (vramPass1 == null ||
                    vramPass1.getWidth() != WINDOW_WIDTH ||
                    vramPass1.getHeight() != WINDOW_HEIGHT ||
                    vramPass1.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                vramPass1 = gc.createCompatibleVolatileImage(WINDOW_WIDTH, WINDOW_HEIGHT, Transparency.OPAQUE);
            }

            precomputeCrtMath();

            int drawX = (int) (currentWidth * CRT_MARGIN);
            int drawY = (int) (currentHeight * CRT_MARGIN);
            int drawW = currentWidth - drawX * 2;
            int drawH = currentHeight - drawY * 2;

            updateCrtOverlay(drawW, drawH);

            boolean loadingComplete = initLoadEnd.get();
            long frameStartNanos = System.nanoTime();

            int retry = 0;
            do {
                if (vramBuffer.validate(gc) == VolatileImage.IMAGE_RESTORED) {}
                Graphics2D vg = vramBuffer.createGraphics();
                try {
                    vg.setColor(Color.BLACK);
                    vg.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
                    drawCurrentFrame(vg, loadingComplete);
                } finally {
                    vg.dispose();
                }
            } while (running.get() && vramBuffer.contentsLost() && ++retry < 3);

            if (!running.get() || vramBuffer.contentsLost()) return;

            retry = 0;
            do {
                if (vramPass1.validate(gc) == VolatileImage.IMAGE_RESTORED) {}
                Graphics2D p1G = vramPass1.createGraphics();
                try {
                    p1G.setColor(Color.BLACK);
                    p1G.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
                    p1G.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    for (int i = 0; i < CRT_SLICES; i++) {
                        p1G.drawImage(vramBuffer,
                                p1_sx1[i], p1_dy1[i], p1_sx2[i], p1_dy2[i],
                                p1_sx1[i], 0, p1_sx2[i], WINDOW_HEIGHT,
                                null);
                    }
                } finally {
                    p1G.dispose();
                }
            } while (running.get() && vramPass1.contentsLost() && ++retry < 3);

            if (!running.get() || vramPass1.contentsLost()) return;

            try {
                retry = 0;
                do {
                    Graphics2D d2 = (Graphics2D) strategy.getDrawGraphics();
                    try {
                        d2.setColor(Color.BLACK);
                        d2.fillRect(0, 0, currentWidth, currentHeight);
                        d2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                        int jitterX = 0;
                        if (crtJitterEnabled && Math.random() < 0.15) {
                            jitterX = (int) (Math.random() * 3) - 1;
                        }
                        int finalDrawX = drawX + jitterX;
                        int prevScreenY2 = drawY;
                        for (int i = 0; i < CRT_SLICES; i++) {
                            int sy1 = (int) (p2_sy1[i] * WINDOW_HEIGHT);
                            int sy2 = (int) (p2_sy2[i] * WINDOW_HEIGHT);

                            int xMargin = (int) (drawW * p2_dxMargin[i]);
                            int dx1 = finalDrawX + xMargin;
                            int dx2 = finalDrawX + drawW - xMargin;

                            int screenY1 = prevScreenY2;
                            int screenY2 = drawY + (int) Math.round(p2_sy2[i] * drawH);
                            prevScreenY2 = screenY2;

                            d2.drawImage(vramPass1, dx1, screenY1, dx2, screenY2, 0, sy1, WINDOW_WIDTH, sy2, null);
                        }

                        if (crtOverlay != null) {
                            d2.drawImage(crtOverlay, finalDrawX, drawY, null);
                        }

                        crtScanlineFlickerTimer += 0.05f;
                        int pulseAlpha = (int) (7 + Math.sin(crtScanlineFlickerTimer) * 7);
                        int clampedAlpha = Math.max(0, Math.min(255, pulseAlpha));
                        d2.setColor(CRT_PULSE_COLORS[clampedAlpha]);
                        d2.fillRect(finalDrawX, drawY, drawW, drawH);

                    } finally {
                        d2.dispose();
                    }
                } while (running.get() && strategy.contentsRestored() && ++retry < 3);

                if (!strategy.contentsLost()) {
                    strategy.show();
                }
            } catch (IllegalStateException ignored) {
            }

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
            return;
        }

        if (renderingOption.equals(RenderingOption.DEFAULT)) {
            ViewMetrics.Snapshot metrics = viewMetrics.getSnapshot();
            boolean loadingComplete = initLoadEnd.get();
            long frameStartNanos = System.nanoTime();

            if (drawMode == DrawMode.BUFFER) {
                GraphicsConfiguration gc = getGraphicsConfiguration();
                if (offscreenBuffer == null ||
                        offscreenBuffer.getWidth() != bufferWidth ||
                        offscreenBuffer.getHeight() != bufferHeight ||
                        offscreenBuffer.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                    offscreenBuffer = gc.createCompatibleVolatileImage(bufferWidth, bufferHeight, Transparency.OPAQUE);
                }

                int retry = 0;
                do {
                    if (offscreenBuffer.validate(gc) == VolatileImage.IMAGE_RESTORED) {}
                    Graphics2D bg = offscreenBuffer.createGraphics();
                    try {
                        bg.setColor(Color.BLACK);
                        bg.fillRect(0, 0, bufferWidth, bufferHeight);
                        bg.scale((double) bufferWidth / WINDOW_WIDTH, (double) bufferHeight / WINDOW_HEIGHT);
                        drawCurrentFrame(bg, loadingComplete);
                    } finally {
                        bg.dispose();
                    }
                } while (running.get() && offscreenBuffer.contentsLost() && ++retry < 3);

                if (!running.get() || offscreenBuffer.contentsLost()) return;

                int targetDrawW = (int) Math.round(WINDOW_WIDTH * metrics.currentScale());
                int targetDrawH = (int) Math.round(WINDOW_HEIGHT * metrics.currentScale());

                try {
                    int lostRetries = 0;
                    do {
                        int restoreRetries = 0;
                        do {
                            Graphics2D d2 = (Graphics2D) strategy.getDrawGraphics();
                            try {
                                d2.setColor(Color.BLACK);
                                d2.fillRect(0, 0, currentWidth, currentHeight);
                                d2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                                d2.drawImage(offscreenBuffer,
                                        metrics.currentXOffset(), metrics.currentYOffset(),
                                        targetDrawW, targetDrawH,
                                        0, 0, bufferWidth, bufferHeight,
                                        null);
                            } finally {
                                d2.dispose();
                            }
                        } while (strategy.contentsRestored() && ++restoreRetries < 3);
                        strategy.show();
                    } while (strategy.contentsLost() && ++lostRetries < 3);
                } catch (Exception e) {
                    throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
                }

            } else {
                try {
                    int lostRetries = 0;
                    do {
                        int restoreRetries = 0;
                        do {
                            Graphics2D d2 = (Graphics2D) strategy.getDrawGraphics();
                            try {
                                d2.setColor(Color.BLACK);
                                d2.fillRect(0, 0, currentWidth, currentHeight);

                                d2.translate(metrics.currentXOffset(), metrics.currentYOffset());
                                d2.scale(metrics.currentScale(), metrics.currentScale());

                                drawCurrentFrame(d2, loadingComplete);
                            } finally {
                                d2.dispose();
                            }
                        } while (strategy.contentsRestored() && ++restoreRetries < 3);

                        strategy.show();
                    } while (strategy.contentsLost() && ++lostRetries < 3);
                } catch (Exception e) {
                    throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
                }
            }

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
        } else if (renderingOption.equals(RenderingOption.LEGACY)) {
            int targetW = (drawMode == DrawMode.BUFFER) ? bufferWidth : currentWidth;
            int targetH = (drawMode == DrawMode.BUFFER) ? bufferHeight : currentHeight;

            if (vramBuffer == null ||
                    vramBuffer.getWidth() != targetW ||
                    vramBuffer.getHeight() != targetH ||
                    vramBuffer.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
                vramBuffer = getGraphicsConfiguration().createCompatibleVolatileImage(targetW, targetH);
            }

            long frameStartNanos = System.nanoTime();
            boolean loadingComplete = initLoadEnd.get();

            int retry = 0;
            do {
                if (vramBuffer.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_RESTORED) {}

                Graphics2D d2 = vramBuffer.createGraphics();
                try {
                    d2.setColor(Color.BLACK);
                    d2.fillRect(0, 0, targetW, targetH);

                    if (drawMode == DrawMode.BUFFER) {
                        d2.scale((double) bufferWidth / WINDOW_WIDTH, (double) bufferHeight / WINDOW_HEIGHT);
                    } else {
                        d2.translate(viewMetrics.getCurrentXOffset(), viewMetrics.getCurrentYOffset());
                        d2.scale(viewMetrics.getCurrentScale(), viewMetrics.getCurrentScale());
                    }

                    drawCurrentFrame(d2, loadingComplete);
                } finally {
                    d2.dispose();
                }

                Graphics hwGraphics = bufferStrategy.getDrawGraphics();
                try {
                    hwGraphics.setColor(Color.BLACK);
                    hwGraphics.fillRect(0, 0, currentWidth, currentHeight);

                    if (drawMode == DrawMode.BUFFER) {
                        int targetDrawW = (int) Math.round(WINDOW_WIDTH * viewMetrics.getCurrentScale());
                        int targetDrawH = (int) Math.round(WINDOW_HEIGHT * viewMetrics.getCurrentScale());
                        hwGraphics.drawImage(vramBuffer,
                                viewMetrics.getCurrentXOffset(), viewMetrics.getCurrentYOffset(),
                                targetDrawW, targetDrawH,
                                0, 0, bufferWidth, bufferHeight,
                                null);
                    } else {
                        hwGraphics.drawImage(vramBuffer, 0, 0, null);
                    }
                } finally {
                    hwGraphics.dispose();
                }
                bufferStrategy.show();

            } while (running.get() && vramBuffer.contentsLost() && ++retry < 3);

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
        }
    }

    private void drawCurrentFrame(Graphics2D d2, boolean loadingComplete) {
        sceneLock.readLock().lock();
        try {
            Scene activeScene = currentScene;
            if (activeScene != null) {
                activeScene.render(d2);
            }
        } finally {
            sceneLock.readLock().unlock();
        }

        if (!loadingComplete || !initLoadEnd.get()) {
            renderLoadingScreen(d2);
        }
        else if (fadeAlpha > 0.0f || isSceneLoading.get()) {
            Composite origComposite = d2.getComposite();
            float alpha = Math.min(1.0f, Math.max(0.0f, fadeAlpha));

            d2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            renderLoadingScreen(d2);
            d2.setComposite(origComposite);
        }

        if (loadingComplete && initLoadEnd.get()) {
            render(d2);
        }

        errorBoxManager.render(d2);
        if (Fw.Debugger.showHitbox) {
            Fw.Debugger.Internal.renderHitbox(d2);
        }
        if (console != null) {
            console.render(d2);
        }
    }

    private void recordPresentedFrame(long renderWorkNanos) {
        fpsCounter++;
        accumulatedRenderWorkNanos += renderWorkNanos;

        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastFpsCheckTime;
        if (elapsedTime < 1_000_000_000L) return;

        currentFps = (int) Math.round(fpsCounter * 1_000_000_000.0 / elapsedTime);
        currentFrameTimeMs = (elapsedTime / 1_000_000.0) / fpsCounter;
        currentRenderWorkTimeMs = (accumulatedRenderWorkNanos / 1_000_000.0) / fpsCounter;

        fpsCounter = 0;
        accumulatedRenderWorkNanos = 0L;
        lastFpsCheckTime = currentTime;
    }

    private void recordCpuPresentedFrame(long cpuWorkNanos) {
        cpuFpsCounter++;
        accumulatedCpuWorkNanos += cpuWorkNanos;

        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastCpuFpsCheckTime;
        if (elapsedTime < 1_000_000_000L) return;

        currentCpuFps = (int) Math.round(cpuFpsCounter * 1_000_000_000.0 / elapsedTime);
        currentCpuWorkTimeMs = (accumulatedCpuWorkNanos / 1_000_000.0) / cpuFpsCounter;

        cpuFpsCounter = 0;
        accumulatedCpuWorkNanos = 0L;
        lastCpuFpsCheckTime = currentTime;
    }

    public int getFps() { return currentFps; }
    public double getFrameTimeMs() { return currentFrameTimeMs; }
    public double getRenderWorkTimeMs() { return currentRenderWorkTimeMs; }
    public int getCpuFps() { return currentCpuFps; }
    public double getCpuWorkTimeMs() { return currentCpuWorkTimeMs; }
    public double getViewScale() { return viewMetrics.getCurrentScale(); }
    public double getRequestedViewScale() { return viewMetrics.getRequestedScale(); }
    public double getPhysicalViewScale() { return viewMetrics.getPhysicalScale(); }
    public boolean isViewScaleSnapped() { return viewMetrics.isScaleSnapped(); }

    public boolean isFractionalViewScale() {
        double scale = getViewScale();
        return Math.abs(scale - Math.rint(scale)) > 0.000_001;
    }

    public boolean isFractionalPhysicalScale() {
        double scale = getPhysicalViewScale();
        return Math.abs(scale - Math.rint(scale)) > 0.000_001;
    }

    protected void onAssetInit(AssetInit assetInit) {}

    public void setUp() {}
    public abstract void init(BaseInit baseInit);
    public void update(double dt) {}
    public void render(Graphics2D g) {}
    public void setMouse(Mouse mouse) {}
    public void setConsole(ConsoleInit consoleInit) {}

    @Override public final int getComponentWidth() { return this.getWidth(); }
    @Override public final int getComponentHeight() { return this.getHeight(); }
    @Override public long getCpuWorkTimeNs() { return (long) (currentCpuWorkTimeMs * 1_000_000.0); }
    @Override public long getGpuWorkTimeNs() { return (long) (currentRenderWorkTimeMs * 1_000_000.0); }

    @Override
    public final double getDeviceScaleX() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        return configuration == null ? 1.0 : configuration.getDefaultTransform().getScaleX();
    }

    @Override
    public final double getDeviceScaleY() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        return configuration == null ? 1.0 : configuration.getDefaultTransform().getScaleY();
    }

    public final int getMouseX() { return viewMetrics.getVirtualMouseX(); }
    public final int getMouseY() { return viewMetrics.getVirtualMouseY(); }

    @Override
    @Internal
    @Deprecated
    public Console getConsole() { return console; }

    public final void save() {
        io.save.save();

        Set<Scene> savedScenes = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Map.Entry<String, Scene> entry : sceneRegistry.entrySet()) {
            Scene scene = entry.getValue();
            if (scene != null && scene.canSave()) {
                scene.DO_NOT_ACCESS.save();
                savedScenes.add(scene);
            }
        }

        Scene active = currentScene;
        if (active != null && !savedScenes.contains(active) && active.canSave()) {
            active.DO_NOT_ACCESS.save();
        }
    }

    public void exit(boolean b) {
        if (!isExiting.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        new Thread(() -> {
            try {
                if (mainLoopThread != null) mainLoopThread.join(2000);
                if (logicThread != null) logicThread.join(2000);
                if (renderThread != null) renderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            boolean logicStopped = (mainLoopThread == null || !mainLoopThread.isAlive()) &&
                    (logicThread == null || !logicThread.isAlive());

            if (mainLoopThread != null && mainLoopThread.isAlive()) {
                mainLoopThread.interrupt();
            }
            if (logicThread != null && logicThread.isAlive()) {
                logicThread.interrupt();
            }
            if (renderThread != null && renderThread.isAlive()) {
                renderThread.interrupt();
            }

            if (logicStopped) {
                save();
                operatorManager.exitOperatorPack.launch();
            } else {
                System.err.println("[Engine] Logic thread failed to terminate cleanly within timeout. Skipping save to prevent corruption.");
            }

            SwingUtilities.invokeLater(() -> {
                BufferStrategy strategy = bufferStrategy;
                bufferStrategy = null;
                if (strategy != null) {
                    strategy.dispose();
                }

                if (graphicsDevice != null) {
                    try {
                        if (originalDisplayMode != null && graphicsDevice.isDisplayChangeSupported()) {
                            graphicsDevice.setDisplayMode(originalDisplayMode);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        graphicsDevice.setFullScreenWindow(null);
                    }
                }

                if (frame != null) {
                    frame.setVisible(false);
                    frame.dispose();
                }
                if (b) {
                    System.exit(0);
                }
            });
        }, "Shutdown-Thread").start();
    }

    public void exit() {
        exit(false);
    }

    private void flushSuppressionNotice() {
        ErrorTracker tracker = errorTracker.get();

        // Flush notices for errors that resolved in the current frame
        for (Integer hash : tracker.prevFrameErrors) {
            if (!tracker.currentFrameErrors.contains(hash)) {
                Integer count = tracker.suppressedCounts.remove(hash);
                String title = tracker.errorTitles.remove(hash);
                if (count != null && count > 0 && title != null) {
                    String notice = String.format("[Engine Exception] ... identical error suppressed for %d consecutive frame(s): %s", count, title);
                    if (console != null) {
                        console.addLog(Console.LogType.ERROR, notice);
                    }
                    System.err.println(notice);
                }
            }
        }

        // Cycle frame error sets
        tracker.prevFrameErrors.clear();
        tracker.prevFrameErrors.addAll(tracker.currentFrameErrors);
        tracker.currentFrameErrors.clear();
    }

    private void logThreadException(Throwable t) {
        if (t == null) return;

        if (t instanceof VirtualMachineError) {
            String fatalMsg = "[FATAL VM ERROR] " + t.getClass().getSimpleName() + ": " + t.getMessage();
            System.err.println(fatalMsg);
            t.printStackTrace(System.err);
            if (console != null) {
                console.addLog(Console.LogType.ERROR, fatalMsg);
            }
            System.exit(1);
            return;
        }

        String threadName = Thread.currentThread().getName();
        StackTraceElement[] traces = t.getStackTrace();
        String exType = t.getClass().getSimpleName();
        String exMsg = (t.getMessage() != null) ? t.getMessage() : "null";

        StringBuilder stackKey = new StringBuilder();
        int depth = (traces != null) ? Math.min(traces.length, 3) : 0;
        for (int i = 0; i < depth; i++) {
            stackKey.append(traces[i].getClassName())
                    .append('.')
                    .append(traces[i].getMethodName())
                    .append(':')
                    .append(traces[i].getLineNumber())
                    .append(';');
        }
        String rootTrace = (depth > 0) ? traces[0].toString() : "unknown-source";

        int errorHash = 31 * threadName.hashCode() + exType.hashCode();
        errorHash = 31 * errorHash + stackKey.toString().hashCode();

        ErrorTracker tracker = errorTracker.get();
        tracker.currentFrameErrors.add(errorHash);

        // Suppress if the error occurred in the previous frame
        if (tracker.prevFrameErrors.contains(errorHash)) {
            int currentSuppressed = tracker.suppressedCounts.merge(errorHash, 1, Integer::sum);
            if (currentSuppressed % 60 == 0) {
                String notice = String.format("[Engine Exception] ... identical error continuously suppressed (%d frames) for [%s] %s", currentSuppressed, threadName, exType);
                if (console != null) {
                    console.addLog(Console.LogType.ERROR, notice);
                }
                System.err.println(notice);
            }
            return;
        }

        String title = String.format("[%s] %s: %s", threadName, exType, exMsg);
        String atLine = " at " + rootTrace;
        tracker.errorTitles.put(errorHash, title);

        if (console != null) {
            String[] lines = title.split("\\r?\\n");
            for (String line : lines) {
                if (!line.isEmpty()) {
                    console.addLog(Console.LogType.ERROR, line);
                }
            }
            console.addLog(Console.LogType.ERROR, atLine);
        }

        System.err.println("[Engine Exception] " + title + " " + atLine);
        t.printStackTrace(System.err);
    }

    private void renderLoadingScreen(Graphics2D g) {
        LoadingRenderer customRenderer = this.loadingRenderer;
        if (customRenderer != null) {
            customRenderer.render(g, loadingProgress, currentLoadingMessage, WINDOW_WIDTH, WINDOW_HEIGHT);
            return;
        }

        if (logo != null && logo.getVolatileImage() != null) {
            g.drawImage(logo.getVolatileImage(), 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, null);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        }
        g.setFont(loadingMessageFont);
        g.setColor(Color.BLACK);
        String displayMsg = String.format("%s (%.0f%%)", currentLoadingMessage, loadingProgress * 100);
        RU.drawStringCenter(g, displayMsg, WINDOW_WIDTH / 2, (int) (WINDOW_HEIGHT * 0.78));
    }

    public GraphicsConfiguration graphicsConfiguration() { return getGraphicsConfiguration(); }

    @Override
    public final java.awt.im.InputMethodRequests getInputMethodRequests() {
        return new java.awt.im.InputMethodRequests() {
            @Override public java.awt.font.TextHitInfo getLocationOffset(int x, int y) { return null; }
            @Override public java.awt.Rectangle getTextLocation(java.awt.font.TextHitInfo offset) { return new java.awt.Rectangle(50, 130, 0, 0); }
            @Override public java.text.AttributedCharacterIterator getSelectedText(java.text.AttributedCharacterIterator.Attribute[] attributes) { return null; }
            @Override public java.text.AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, java.text.AttributedCharacterIterator.Attribute[] attributes) { return null; }
            @Override public int getCommittedTextLength() { return 0; }
            @Override public int getInsertPositionOffset() { return 0; }
            @Override public java.text.AttributedCharacterIterator cancelLatestCommittedText(java.text.AttributedCharacterIterator.Attribute[] attributes) { return null; }
        };
    }

    public class ErrorBoxManager {
        private static final int MAX_BOXES = 5;
        private static final long DURATION_NANOS = 5_000_000_000L;
        private static final CopyOnWriteArrayList<ErrorBox> boxes = new CopyOnWriteArrayList<>();

        public static class ErrorBox {
            final String title;
            final String message;
            final long expireTimeNanos;

            public ErrorBox(String title, String message) {
                this.title = title;
                this.message = message;
                this.expireTimeNanos = System.nanoTime() + DURATION_NANOS;
            }

            public boolean isExpired(long nowNanos) {
                return nowNanos >= expireTimeNanos;
            }
        }

        public void addError(String title, String message) {
            if (boxes.size() >= MAX_BOXES) {
                boxes.remove(0);
            }
            boxes.add(new ErrorBox(title, message));
        }

        public void render(Graphics g) {
            if (boxes.isEmpty()) return;

            long now = System.nanoTime();
            boxes.removeIf(box -> box.isExpired(now));

            int boxWidth = 360;
            int boxHeight = 60;
            int margin = 10;
            int startX = WINDOW_WIDTH - boxWidth - 20;
            int startY = WINDOW_HEIGHT - boxHeight - 20;

            Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
            Font descFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

            for (int i = 0; i < boxes.size(); i++) {
                ErrorBox box = boxes.get(boxes.size() - 1 - i);
                int y = startY - (i * (boxHeight + margin));

                g.setColor(new Color(40, 0, 0, 220));
                g.fillRect(startX, y, boxWidth, boxHeight);
                g.setColor(new Color(255, 60, 60));
                g.drawRect(startX, y, boxWidth, boxHeight);

                g.setColor(Color.RED);
                g.setFont(titleFont);
                g.drawString(box.title, startX + 10, y + 22);

                g.setColor(Color.WHITE);
                g.setFont(descFont);
                g.drawString(box.message, startX + 10, y + 45);
            }
        }
    }

    public void addLog(String str) {
        System.out.println(str);
        console.addLog(Console.LogType.SYSTEM, str);
    }

    public void addErrorLog(String str) {
        System.err.println(str);
        console.addLog(Console.LogType.ERROR, str);
    }
}