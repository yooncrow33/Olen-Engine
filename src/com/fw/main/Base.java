package com.fw.main;

import com.fw.internal.sys.base.view.AccessConsole;
import com.fw.internal.sys.input.MouseAtBase;
import com.fw.internal.utils.Internal;
import com.fw.main.api.io.Io;
import com.fw.internal.sys.operator.OperatorManager;
import com.fw.internal.sys.base.view.IFrameSize;
import com.fw.internal.sys.base.view.ViewMetrics;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.api.sys.ConsoleCMD;
import com.fw.main.api.sys.graphics.Call;
import com.fw.main.utils.graphics.RU;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.input.korean.TextModule;
import com.fw.main.utils.input.mouse.MouseInterface;
import com.fw.internal.utils.DynamicAsset;
import com.fw.main.utils.platform.system.asset.*;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.MusicAsset;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.SoundAsset;
import com.fw.main.utils.platform.system.console.Console;
import com.fw.main.utils.platform.system.console.autoComplete.AutoCompleteManager;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public abstract class Base extends Canvas implements IFrameSize, PerformanceRecorder.BaseWorkTimeProvider, AccessConsole {
    private static final long RESIZE_SETTLE_NANOS = 150_000_000L;

    PerformanceRecorder.CaptureMode captureMode = PerformanceRecorder.CaptureMode.DO_NOT;
    public static String version = "PRE 0.2.0 in dev";
    public JFrame frame = new JFrame("Olen Engine");

    // --- Fullscreen and resolution states ---
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
    final Io io = new Io();
    final OperatorManager operatorManager = new OperatorManager();
    private final AssetInit assetInit = new AssetInit(this);
    private final BaseInit baseInit = new BaseInit(this);
    final AtomicBoolean initLoadEnd = new AtomicBoolean(false);
    final ArrayList<DynamicAsset> sysLoadStack = new ArrayList<>();
    public final AtomicBoolean isChangeScene = new AtomicBoolean(false);
    public final AtomicBoolean isSceneLoading = new AtomicBoolean(false);
    public Scene getCurrentScene() { return currentScene; }
    volatile Scene currentScene = null;
    volatile Scene pendingScene;

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

    private final ArrayList<Call> drawCalls = new ArrayList<>(1024);
    private final ArrayList<Integer> drawCallXs = new ArrayList<>(1024);
    private final ArrayList<Integer> drawCallYs = new ArrayList<>(1024);

    private final ArrayList<Call> renderTargetCalls = new ArrayList<>(1024);
    private final ArrayList<Integer> renderTargetXs = new ArrayList<>(1024);
    private final ArrayList<Integer> renderTargetYs = new ArrayList<>(1024);

    private TextModule textModule;
    private volatile MouseAtBase mouseAtBase = new MouseAtBase(this);

    private volatile PerformanceRecorder recorder;
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

    /** Creates an engine. A null builder can be supplied later with {@link #setBuilder(Builder)}. */
    public Base(Builder builder) {
        if (builder != null) initializeBuilder(builder);
    }

    /** Applies a builder once, before the engine is launched. */
    public synchronized void setBuilder(Builder builder) {
        if (builderInitialized) throw new IllegalStateException("builder is already initialized");
        initializeBuilder(java.util.Objects.requireNonNull(builder, "builder"));
    }

    /** Applies a builder and starts the engine. */
    public void setBuilderAndLaunch(Builder builder) {
        setBuilder(builder);
        launch();
    }

    /** Returns whether the builder has been applied. */
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

        // Bind fullscreen and resolution options
        this.fullScreen = builder.fullScreen;
        this.changeResolution = builder.changeResolution;
        this.targetResWidth = builder.targetResWidth;
        this.targetResHeight = builder.targetResHeight;
        this.targetBitDepth = builder.targetBitDepth;
        this.targetRefreshRate = builder.targetRefreshRate;
        this.WINDOW_WIDTH = builder.virtualScreenWidth;
        this.WINDOW_HEIGHT = builder.virtualScreenHeight;
        this.useEngineCursor = builder.useEngineCursor;

        viewMetrics = new ViewMetrics(this, Core.get().isUseIntegerPhysicalScaling(),WINDOW_WIDTH,WINDOW_HEIGHT);

        sysLoadStack.add(() -> {
            if (builder.integerKey != null) { Fw.add(builder.integerKey, this); }
            if (builder.stringKey != null) { Fw.add(builder.stringKey, this); }
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
            captureMode = builder.performanceRecorderOption;
            if (captureMode == PerformanceRecorder.CaptureMode.DO_NOT) return;

            this.recorder = new PerformanceRecorder(this, this, builder.performanceRecorderOption);
            recorder.setPhase("launch");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                recorder.exit(builder.dumpPerformanceDataFileName);
            }));
        });
        builder.saveValues();
        builderInitialized = true;
    }

    public enum WaitMode {
        OS_SLEEP, HYBRID, BUSY_WAIT
    }

    /** Configures the base renderer and startup services. */
    public static class Builder {
        Config.Builder coreConfig;
        Config coreConfigValue;
        String stringKey;
        Integer integerKey;
        boolean consoleUse;
        PerformanceRecorder.CaptureMode performanceRecorderOption = PerformanceRecorder.CaptureMode.DO_NOT;
        String dumpPerformanceDataFileName;
        RenderingOption renderingOption = RenderingOption.DEFAULT;
        WaitMode waitMode = WaitMode.OS_SLEEP;
        boolean closeWindowWithKillVM = true;
        boolean crtJitterEnabled = true;
        String title = "null";
        int virtualScreenWidth = 1920;
        int virtualScreenHeight = 1080;
        boolean useEngineCursor = false;

        // Fullscreen and resolution configuration fields
        boolean fullScreen = false;
        boolean changeResolution = false;
        int targetResWidth = -1;
        int targetResHeight = -1;
        int targetBitDepth = DisplayMode.BIT_DEPTH_MULTI;
        int targetRefreshRate = DisplayMode.REFRESH_RATE_UNKNOWN;

        /** Supplies Core configuration without a static initializer. */
        public Builder setCoreConfig(Config.Builder config) {
            coreConfig = java.util.Objects.requireNonNull(config); coreConfigValue = null; return this;
        }
        /** Supplies an already built Core configuration without a static initializer. */
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

        /** Sets whether to use full screen */
        public Builder setFullScreen(boolean fullScreen) {
            this.fullScreen = fullScreen;
            return this;
        }

        /** Sets the forced resolution on entering full screen (width x height) */
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

        /** Sets detailed display parameters on entering full screen (width x height x bit depth x refresh rate) */
        public Builder setDisplayResolution(int width, int height, int bitDepth, int refreshRate) {
            this.changeResolution = true;
            this.targetResWidth = width;
            this.targetResHeight = height;
            this.targetBitDepth = bitDepth;
            this.targetRefreshRate = refreshRate;
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
            } catch (NumberFormatException ignored) {}

            try {
                renderingOption = RenderingOption.valueOf(SettingFiles.read("base.renderingOption", renderingOption.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                waitMode = WaitMode.valueOf(SettingFiles.read("base.waitMode", waitMode.name()));
            } catch (IllegalArgumentException ignored) { }
            try {
                performanceRecorderOption = PerformanceRecorder.CaptureMode.valueOf(
                        SettingFiles.read("base.performanceRecorderOption", performanceRecorderOption.name()));
            } catch (IllegalArgumentException ignored) { }
            dumpPerformanceDataFileName = SettingFiles.read("base.dumpPerformanceDataFileName", dumpPerformanceDataFileName);
            if ("".equals(dumpPerformanceDataFileName)) dumpPerformanceDataFileName = null;
        }

        private void saveValues() {
            SettingFiles.write("base.consoleUse", Boolean.toString(consoleUse));
            SettingFiles.write("base.renderingOption", renderingOption.name());
            SettingFiles.write("base.waitMode", waitMode.name());
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
        }

        private static boolean savedBoolean(String name, boolean fallback) {
            String value = SettingFiles.read(name, Boolean.toString(fallback));
            return "true".equalsIgnoreCase(value) ? true : "false".equalsIgnoreCase(value) ? false : fallback;
        }
    }

    /** Stores one setting in each properties file under the current project directory. */
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
        public Mouse(Base base) { this.base = base; }
        public int x() { return base.getMouseX(); }
        public int y() { return base.getMouseY(); }
        public void registerMouseInterface(MouseInterface mouseInterface) { mouseAtBase.registerInterface(mouseInterface); }
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
        public void setInitScene(Scene initScene) { this.initScene = initScene; }
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
                frame.setVisible(true); // Required before binding FSEM window on some platforms

                graphicsDevice.setFullScreenWindow(frame);
                originalDisplayMode = graphicsDevice.getDisplayMode();

                // When forced resolution change is requested
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

    private void startAsyncLoading() {
        loadTasks.clear();

        for (DynamicAsset dynamicAsset : sysLoadStack) {
            loadTasks.add(new LoadTask("Configuring System...", dynamicAsset::load));
        }

        for (Map.Entry<String, InputStream> entry : assetInit.textureAssets.entrySet()) {
            String key = entry.getKey();
            InputStream is = entry.getValue();
            loadTasks.add(new LoadTask("Loading texture: " + key, () -> {
                Texture real = assetManager.loadTexture(AssetManager.LoadMode.SYNC, key, is, null);
                BootTextureProxy proxy = assetInit.textureProxies.get(key);
                if (proxy != null) proxy.setTarget(real);
            }));
        }

        for (Map.Entry<String, InputStream> entry : assetInit.soundAssets.entrySet()) {
            String key = entry.getKey();
            InputStream is = entry.getValue();
            loadTasks.add(new LoadTask("Loading sound: " + key, () -> {
                SoundAsset real = assetManager.loadSound(AssetManager.LoadMode.SYNC, key, is, null);
                AssetInit.BootSoundProxy proxy = assetInit.soundProxies.get(key);
                if (proxy != null) proxy.setTarget(real);
            }));
        }

        for (Map.Entry<String, InputStream> entry : assetInit.musicAssets.entrySet()) {
            String key = entry.getKey();
            InputStream is = entry.getValue();
            loadTasks.add(new LoadTask("Loading music: " + key, () -> {
                MusicAsset real = assetManager.loadMusic(AssetManager.LoadMode.SYNC, AssetManager.MusicType.STREAM_MUSIC, key, is, null);
                AssetInit.BootMusicProxy proxy = assetInit.musicProxies.get(key);
                if (proxy != null) proxy.setTarget(real);
            }));
        }

        loadTasks.add(new LoadTask("Loading Game Data...", () -> {
            io.load.loadStart = true;
            io.load.load();
            io.load.loadEnd = true;
        }));

        Scene initialScene = baseInit.initScene;
        if (initialScene != null) {
            loadTasks.add(new LoadTask("Initializing Scene: " + initialScene.name, () -> {
                initialScene.base = this;
                initialScene.init();
            }));
        }

        new Thread(() -> {
            int total = loadTasks.size();
            if (total == 0) return;

            for (int i = 0; i < total; i++) {
                LoadTask task = loadTasks.get(i);
                System.out.println(task.description);
                currentLoadingMessage = task.getDescription();
                loadingProgress = (float) i / total;

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
        init(baseInit);
        if (Core.get().loadingScreenTexture != null) {
            logo = assetManager.loadTexture(AssetManager.LoadMode.SYNC, "engine_logo", Core.get().loadingScreenTexture, null);
        } else {
            //ErrorBoxManager.addError("Custom Loading Screen Load Fail", "instead to default screen.");
            logo = assetManager.loadTexture(AssetManager.LoadMode.SYNC, "engine_logo", InternalUtils.getEngineResourceStream("Olen.png"), null);
        }

        threadLaunch();
        startAsyncLoading();
    }

    private void threadLaunch() {
        System.out.println(InternalUtils.Time.getTimeFormate() + " / logic thread start");

        running.set(true);
        logicThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            long nextTick = lastTime;
            final double targetFps = 60.0;
            final long nsPerTick = (long) (1000000000.0 / targetFps);

            while (running.get()) {
                long now = System.nanoTime();
                double deltaTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                try {
                    if (initLoadEnd.get() && !isSceneLoading.get()) {
                        long frameStartNanos = System.nanoTime();
                        update(deltaTime);
                        if (recorder != null) recorder.update();
                        recordCpuPresentedFrame(System.nanoTime() - frameStartNanos);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                nextTick += nsPerTick;
                if (System.nanoTime() - nextTick >= nsPerTick) nextTick = System.nanoTime();
                waitForNextTick(nextTick);
            }
        });

        logicThread.setName("logicLoop");
        logicThread.start();

        System.out.println(InternalUtils.Time.getTimeFormate() + " / render thread start");

        running.set(true);
        renderThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            long nextTick = lastTime;
            final double targetFps = 60.0;
            final long nsPerTick = (long) (1000000000.0 / targetFps);

            while (running.get()) {
                long now = System.nanoTime();
                lastTime = now;

                try {
                    if (isChangeScene.compareAndSet(true, false)) {
                        Scene nextScene = this.pendingScene;
                        this.pendingScene = null;

                        if (nextScene != null) {
                            isSceneLoading.set(true);

                            new Thread(() -> {
                                try {
                                    nextScene.init();

                                    if (currentScene != null) {
                                        Method method = currentScene.getClass().getDeclaredMethod("dispose");
                                        method.setAccessible(true);
                                        method.invoke(currentScene);
                                    }

                                    currentScene = nextScene;
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                    System.err.println("Asset loading failed! Aborting engine.");
                                    System.exit(1);
                                } finally {
                                    isSceneLoading.set(false);
                                }
                            }, "Scene-Loader").start();
                        }
                    }
                    renderLoop();
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                nextTick += nsPerTick;
                if (System.nanoTime() - nextTick >= nsPerTick) nextTick = System.nanoTime();
                waitForNextTick(nextTick);
            }
        });

        renderThread.setName("renderLoop");
        renderThread.start();
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
            //Thread.currentThread().interrupt();
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

        // Base.java - renderLoop() 내 CRT 렌더링 블록

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

            // 1. Pass 0 (Base Scene -> vramBuffer)
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
            } while (running.get() && vramBuffer.contentsLost());

            if (!running.get()) return;

            // 2. Pass 1 (Horizontal Curvature -> vramPass1)
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
            } while (running.get() && vramPass1.contentsLost());

            if (!running.get()) return;

            // 3. Final Pass (Vertical Curvature + Shaders -> BufferStrategy Canvas)
            try {
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
                } while (running.get() && strategy.contentsRestored());

                if (!strategy.contentsLost()) {
                    strategy.show();
                }
            } catch (IllegalStateException e) {
                // BufferStrategy가 해제되었거나 윈도우가 파괴 중일 때 조용히 탈출
            }

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
            return;
        }

        if (renderingOption.equals(RenderingOption.DEFAULT) || renderingOption.equals(RenderingOption.EXPERIMENTAL)) {
            ViewMetrics.Snapshot metrics = viewMetrics.getSnapshot();
            boolean loadingComplete = initLoadEnd.get();
            boolean experimentalFrame = loadingComplete && renderingOption.equals(RenderingOption.EXPERIMENTAL);
            if (experimentalFrame) {
                prepareExperimentalFrame();
            }

            long frameStartNanos = System.nanoTime();
            try {
                do {
                    do {
                        Graphics2D d2 = (Graphics2D) strategy.getDrawGraphics();
                        try {
                            d2.setColor(Color.BLACK);
                            d2.fillRect(0, 0, currentWidth, currentHeight);

                            d2.translate(
                                    metrics.currentXOffset(),
                                    metrics.currentYOffset()
                            );
                            d2.scale(
                                    metrics.currentScale(),
                                    metrics.currentScale()
                            );

                            drawCurrentFrame(d2, loadingComplete);
                        } finally {
                            d2.dispose();
                        }
                    } while (strategy.contentsRestored());

                    strategy.show();
                } while (strategy.contentsLost());
            } finally {
                if (experimentalFrame) {
                    clearExperimentalFrame();
                }
            }

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
        } else if (renderingOption.equals(RenderingOption.LEGACY)) {
            if (vramBuffer == null ||
                    vramBuffer.getWidth() != currentWidth ||
                    vramBuffer.getHeight() != currentHeight ||
                    vramBuffer.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
                vramBuffer = getGraphicsConfiguration().createCompatibleVolatileImage(currentWidth, currentHeight);
            }

            long frameStartNanos = System.nanoTime();

            do {
                if (vramBuffer.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_RESTORED) {}

                Graphics2D d2 = vramBuffer.createGraphics();
                try {
                    d2.setColor(Color.BLACK);
                    d2.fillRect(0, 0, currentWidth, currentHeight);

                    d2.translate(viewMetrics.getCurrentXOffset(), viewMetrics.getCurrentYOffset());
                    d2.scale(viewMetrics.getCurrentScale(), viewMetrics.getCurrentScale());

                    if (!initLoadEnd.get() || isSceneLoading.get()) {
                        renderLoadingScreen(d2);
                    } else {
                        render(d2);
                    }

                    if (Fw.Debugger.showHitbox) {
                        Fw.Debugger.Internal.renderHitbox(d2);
                    }
                    if (console != null) { console.render(d2); }

                } finally {
                    d2.dispose();
                }

                Graphics hwGraphics = bufferStrategy.getDrawGraphics();
                try {
                    hwGraphics.drawImage(vramBuffer, 0, 0, null);
                } finally {
                    hwGraphics.dispose();
                }
                bufferStrategy.show();

            } while (vramBuffer.contentsLost());

            recordPresentedFrame(System.nanoTime() - frameStartNanos);
        }
    }

    private void prepareExperimentalFrame() {
        synchronized (drawCalls) {
            renderTargetCalls.addAll(drawCalls);
            renderTargetXs.addAll(drawCallXs);
            renderTargetYs.addAll(drawCallYs);

            drawCalls.clear();
            drawCallXs.clear();
            drawCallYs.clear();
        }
    }

    private void clearExperimentalFrame() {
        renderTargetCalls.clear();
        renderTargetXs.clear();
        renderTargetYs.clear();
    }

    private void drawCurrentFrame(Graphics2D d2, boolean loadingComplete) {
        if (!loadingComplete) {
            renderLoadingScreen(d2);
        } else if (renderingOption.equals(RenderingOption.EXPERIMENTAL)) {
            for (int i = 0; i < renderTargetCalls.size(); i++) {
                Call call = renderTargetCalls.get(i);
                int x = renderTargetXs.get(i);
                int y = renderTargetYs.get(i);

                if (call != null) {
                    call.updateCache();
                    VolatileImage buffer = call.getBuffer();
                    if (buffer != null) {
                        d2.drawImage(buffer, x, y, null);
                    }
                }
            }
        } else {
            if (!initLoadEnd.get() || isSceneLoading.get()) {
                renderLoadingScreen(d2);
            } else {
                render(d2);
            }
        }

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

    public abstract void init(BaseInit baseInit);
    public abstract void update(double dt);
    public abstract void render(Graphics2D g);
    public void setMouse(Mouse mouse) {}
    public void setConsole(ConsoleInit consoleInit) {}
    public void experimentalRendering(Renderer r) {}

    public synchronized boolean changeScene(Scene newScene) {
        if (newScene == null) {
            System.err.println("new scene is null!");
            return false;
        }
        if (isSceneLoading.get() || isChangeScene.get()) {
            return false;
        }

        newScene.base = this;
        this.pendingScene = newScene;

        return this.isChangeScene.compareAndSet(false, true);
    }

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

    public final void save() { io.save.save(); }

    public void exit(boolean b) {
        if (!isExiting.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        new Thread(() -> {
            try {
                if (logicThread != null) logicThread.join(2000);
                if (renderThread != null) renderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            boolean logicStopped = (logicThread == null || !logicThread.isAlive());
            if (!logicStopped) {
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

    private void renderLoadingScreen(Graphics g) {
        g.drawImage(logo.getVolatileImage(), 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, null);
        g.setFont(loadingMessageFont);
        g.setColor(Color.black);
        String displayMsg = String.format("%s (%.0f%%)", currentLoadingMessage, loadingProgress * 100);
        RU.drawStringCenter(g, displayMsg, 960, 850);
        g.setColor(Color.black);
        errorBoxManager.render(g);
    }

    private void addDrawCall(int x, int y, Call call) {
        synchronized (drawCalls) {
            drawCalls.add(call);
            drawCallXs.add(x);
            drawCallYs.add(y);
        }
    }

    public class Renderer {
        public void addDrawCall(int x, int y, Call call) {
            Base.this.addDrawCall(x, y, call);
        }
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
        console.addLog(Console.LogType.SYSTEM,str);
    }


    public void addErrorLog(String str) {
        System.out.println(str);
        console.addLog(Console.LogType.SYSTEM ,str);
    }
}