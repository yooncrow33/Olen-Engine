# Olen Engine (PRE 0.4.0)

A lightweight 2D game engine based on Java/AWT. It features high-performance memory management, dynamic texture atlas packing, a completed Hangul (Korean) input module, static analysis/IDE assistance for resource and scene string keys, an interactive developer console with tree-based autocompletion, an embedded performance profiling and telemetry suite, and a robust scene lifecycle with smooth visual transitions.

---

# CHANGE LOG

## PRE 0.4.0
- **Scene Architecture & Transition Pipeline**
    * Refactored `Scene` into an extensible abstract class with full lifecycle hooks: `init(SceneInit)`, `update(dt)`, `render(Graphics2D)`, `onEnter()`, `onExit()`, and `onDispose()`
    * Added asynchronous scene transition pipeline with configurable fade speeds (`setFadeSpeed`) and states (`TransitionPhase`: `TRANSITION_FADE_OUT`, `TRANSITION_LOADING`, `TRANSITION_FADE_IN`)
    * Customizable loading visualizer hook via `LoadingRenderer`
    * Dedicated scene-level persistence API: `saveable`, `save(Properties)`, and `load(Properties)` storing persistent state under `~/.{projectName}/save/{sceneName}.properties`
    * Integrated scene registry (`registerScene`, `changeScene`) and scene-scoped audio management (`Sfx`, `Bgm`)
- **IDE Plugin (`OlenEngineSupport`) Scene Key Integration**
    * Extended static indexing and code assistance to scene identifier keys (`SCENE`): autocompletion, declaration jumping, error checking, and refactoring for `changeScene("")` and `setInitScene("")`
- **Asset Pipeline & Concurrency Overhaul**
    * Unified `LoadMode` (`SYNC`, `LAZY`) across `loadTexture`, `loadSound`, and `loadMusic`
    * Added `DynamicAssetObject` featuring latch-based synchronization (`awaitCompletion`, `awaitCompletionOrThrow`), cancellation, and memory pool recycling
    * Boot asset registration via `AssetInit` returning proxy handles (`BootTextureProxy`, `BootSoundProxy`, `BootMusicProxy`) and boot-time atlas pre-baking (`registerBootAtlas`)
    * Introduced direct bitmap registration: `registerDirectTexture(assetKey, BufferedImage)`
    * Extended resource release (`free`) to support `ATLAS` alongside `TEXTURE`, `SOUND`, and `MUSIC`
- **Graphics Pipeline & Display Control**
    * Multi-mode rendering architecture: `DrawMode.DIRECT` and `DrawMode.BUFFER` (offscreen `VolatileImage` scaling)
    * Expanded `RenderingOption` with `LEGACY` (safe fallback for macOS window resizing) and modernized `CRT`
    * Enhanced CRT emulation: 64-slice dual-pass barrel distortion curvature (`CURVE_X`, `CURVE_Y`), CRT jitter simulation (`crtJitterEnabled`), scanlines, phosphor mask, soft vignette, and dynamic pulse flicker
    * Display management: Fullscreen Exclusive Mode (FSEM), dynamic resolution and refresh rate switching (`setDisplayResolution`), and configurable hardware cursor sizing (`useEngineCursor`, `setCursorSize`)
    * Snapped integer physical scaling calculations with high-DPI device transform correction (`ViewMetrics`, `sanitizeDeviceScale`)
    * Virtual coordinate unwarping for mouse input under CRT barrel curvature (`updateVirtualMouseCrt`)
- **Developer Console & Command System**
    * Modular tree-based autocompletion engine (`AutoCompleteManager`, `TreeBuilder`, `CandidateRule`, `Condition`)
    * Dual-pane suggestion interface: candidate pool on the left, ranked matching suggestions on the right with arrow key navigation
    * Real-time syntax and whitespace warning indicators (unclosed quotes, consecutive space detection)
    * Built-in `sys` system command namespace:
        - `sys scene <sceneKey>`: Live runtime scene transitions
        - `sys debug asset <texture|sound|music|atlas> <key> [desktop|projectFolder|exeFolder]`: Instant asset inspection and image dumping
        - `sys quickput <put|delete|clear>`: User macro/alias system with auto-updating autocomplete and persistent disk storage
        - `sys exe <performanceReader|settingEditor|testInConsole>`: Launch embedded diagnostic tools directly from the console
        - `sys mem`, `sys thread`, `sys dump`, `sys getInfo`: Real-time JVM memory, thread dump, and system information diagnostics
- **Diagnostic Tools & Performance Profiling**
    * Standalone visualizer GUI: `PerformanceReader` with multi-metric graph tabs (`GraphTab`), interactive pan/zoom, dynamic cap clipping, phase range markers, and tick metrics
    * Comprehensive telemetry collection (`PerformanceRecorder`): CPU usage, JVM heap/non-heap, GC pause times, frame time, logic/render work times, OS architecture, hardware specs, network interfaces, and disk volumes
    * Runtime configuration GUI: `EngineSettingEditor` for modifying individual engine parameters on disk with reload, validation, and full directory purge capabilities
- **Fault Tolerance & Stability**
    * Per-thread exception throttling (`ErrorTracker`): Suppresses duplicate exceptions across consecutive frames to prevent console/terminal flooding
    * Visual on-screen HUD toast notifications (`ErrorBoxManager`) displaying runtime and asset loading errors

## PRE 0.2.0
- Resource key static indexing and IntelliJ IDEA plugin (`OlenEngineSupport`) integration
    * Autocompletion, go-to-declaration, real-time error checking, and batch renaming for string resource keys (`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`)
- Standardized resource management API and asset pipeline
    * Asynchronous asset loading completion event key (`EVENT`) integration
    * Explicit resource release API: `free(TYPE, key)`
    * Dedicated key indexing for global framework context: `com.fw.main.Fw`
    * Introduced `Safe Runtime` mode: prevents crash exceptions on load failures with a fallback texture (`wrongTexture`), supports automatic asset pool expansion
- Enhanced stability and lifecycle
    * Eliminated potential race conditions during scene transitions and engine shutdown
- Rendering pipeline and display control
    * Added CRT shader and simulation (`RenderingOption.CRT`)
    * Support for Fullscreen Exclusive Mode (FSEM) and dynamic display mode switching (resolution, bit depth, refresh rate)
    * Custom virtual screen resolution configuration (`virtualScreenWidth`, `virtualScreenHeight`)
    * Engine-dedicated custom hardware cursor (`EngineCursor.png`) support
    * Removed experimental draw call caching system (`RenderingOption.EXPERIMENTAL`, `Call`, `LazyCall`, `StaticCall`)
- Frame timing
    * Introduced precise frame synchronization wait modes (`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`)
- Input and debugging/console system improvements
    * Optimized `KeyBindingBase` input processing: eliminated Swing `ActionMap` overhead, switched to lightweight 65,536 static key table indexing, fixed inverted binding options bug
    * Responsive console UI and autocompletion navigation improvements: UI scaling bound to screen resolution, arrow key (UP/DOWN) command suggestion navigation
    * Added persistent on-screen debug HUD

## PRE 0.1.0
- Initialization architecture improvements
    * Switched from default constructor-based initialization to explicit initialization (`init`)
    * Support for returning proxy objects during `AssetInit` bootstrapping
- Performance profiling
    * Added `PerformanceRecorder` and `PerformanceReader`
- Sound
    * Rolled audio engine back to `TinySound`

---

## IntelliJ IDEA Plugin Guide

Many resources and transitions within the engine (textures, sounds, sprites, scenes, etc.) are identified by string keys. Installing the dedicated plugin is highly recommended to prevent typos and ensure smooth code navigation.

- Repository: https://github.com/yooncrow33/OlenEnginePlugin

### Plugin Features
1. **Auto-completion**: Provides registered key popups inside resource and scene navigation methods such as `getTexture("")`, `changeScene("")`, or `setInitScene("")`
2. **Go to Declaration (Ctrl + Click)**: Jump directly from a usage string literal to its declaration point (e.g., `registerBootTexture`, `registerScene`)
3. **Syntax / Error Inspection**: Real-time warning highlights (red underline) for unregistered or mistyped keys
4. **Rename Refactoring (Shift + F6)**: Simultaneously refactors the registration declaration and all usages across the project when renaming a key

### Notice
- Tested exclusively on #IC-252.26199.169.

---

## Key Features

- **Advanced Graphics & Rendering Pipeline**
    * Hardware-accelerated double buffering backed by `VolatileImage` and configurable `BufferStrategy` buffers
    * Multi-mode rendering: Direct graphics drawing (`DrawMode.DIRECT`) or intermediate offscreen buffer scaling (`DrawMode.BUFFER`)
    * Dynamic texture atlas packing via the MaxRects bin packing algorithm with automated transparent border alpha-trimming
    * Fractional and snapped integer physical scaling calculations (`ViewMetrics`) with high-DPI OS transform compensation
    * Legacy rendering mode for macOS stability and multi-slice CRT barrel distortion with scanlines, glare, and jitter

- **Scene Management & Transition Architecture**
    * First-class `Scene` lifecycle management with clean state separation
    * Built-in asynchronous transition pipeline featuring configurable fade speeds and arbitrary loading visualizer hooks (`LoadingRenderer`)
    * Scene-level data serialization hooks (`saveable`, `save`, `load`) stored automatically under user application data

- **High-Performance Audio Engine (TinySound 1.1.1)**
    * Low-latency PCM signed 44.1kHz 16-bit audio mixer with sub-thread auto-updating
    * Memory-resident playback (`MemSound`, `MemMusic`) and disk streaming (`StreamMusic`, `StreamSound`)
    * Fine-grained control over volume, stereo panning (`[-1.0, 1.0]`), loop points by frame or seconds, and global master volume

- **Developer Experience & Tooling**
    * In-game developer console (`~` key) backed by a tree-structured autocompletion engine and input syntax validation
    * Korean text composition and input method integration (`TextModule`, `TextObject`) supporting clipboard paste and visual cursor rendering
    * In-engine diagnostic suite: `PerformanceRecorder` collecting telemetry dumps (`.fwD`) and `PerformanceReader` providing interactive multi-tab data visualization
    * Dedicated settings manager GUI (`EngineSettingEditor`) with per-property file persistence under `~/.{projectName}/EngineSettings/`
    * Automated error throttling and visual on-screen error HUD notification banners (`ErrorBoxManager`)

---

## Requirements

- **JDK:** OpenJDK 21+ (GraalVM 21+ recommended)
- **OS:** Windows / macOS / Linux

---

## Getting Started

```java
import com.fw.main.*;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.AssetManager;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
import com.fw.main.utils.platform.system.scene.Scene;

import java.awt.*;

public class MyGame extends Base {

    public MyGame() {
        super(new Base.Builder()
                // Initialize Core config directly through Base.Builder
                .setCoreConfig(new Config.Builder("MyGameFolder")
                        .setWindowWidth(1280) // Initial window width
                        .setWindowHeight(720) // Initial window height
                        .setUseKoreanModule(true) // Korean input composition module
                        .setUseIntegerPhysicalScaling(true) // Snaps display scaling to integer multiples
                        .setUseEncryption(false) // Save file encryption toggle
                        .setSafeRuntime(true) // Fallbacks instead of crashing on missing assets
                )
                // Window & Display Options
                .setTitle("My Olen Game")
                .setFullScreen(false)
                .setDisplayResolution(1920, 1080, DisplayMode.BIT_DEPTH_MULTI, DisplayMode.REFRESH_RATE_UNKNOWN)
                .setVirtualScreenWidth(1920) // Logical rendering canvas width
                .setVirtualScreenHeight(1080) // Logical rendering canvas height
                .setUseEngineCursor(true) // Engine custom hardware cursor
                .setCursorSize(32, 32)
                
                // Pipeline & Execution Flow
                .setRenderingOption(RenderingOption.DEFAULT) // DEFAULT, LEGACY, or CRT
                .setDrawMode(Base.DrawMode.DIRECT) // DIRECT or BUFFER
                .setBufferSize(1920, 1080) // Buffer size for BUFFER DrawMode
                .setWaitMode(Base.WaitMode.HYBRID) // OS_SLEEP, HYBRID, or BUSY_WAIT
                .setThreadMode(Base.ThreadMode.SINGLE) // SINGLE (recommended) or DUAL
                .setCrtJitterEnabled(true) // Subtle scanline jitter for CRT mode
                
                // Transition & Timing Parameters
                .setFadeSpeed(3.0f) // Scene crossfade speed
                .setLoadingRenderer((g, progress, message, w, h) -> {
                    // Custom loading visualizer screen hook
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                    g.setColor(Color.WHITE);
                    g.drawString(message + " (" + (int)(progress * 100) + "%)", w / 2 - 100, h / 2);
                })
                .setResizeSettleNanos(150_000_000L) // Wait time on window resize to avoid swapchain tears
                .setShutdownTimeoutMs(2000) // Thread join timeout on engine exit
                .setBufferStrategyCount(2) // Double buffering
                .setRenderRetryCount(3) // Surface restoration retries
                .setCloseWindowWithKillVM(true) // Terminate VM process on window closing
                
                // Diagnostics & Context Binds
                .setUseConsole(true) // Developer console toggle (~ key)
                .setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01")
                .setStringKey("MAIN_GAME_INSTANCE")
                .setIntegerKey(1)
        );
    }

    @Override
    public void init(BaseInit init) {
        // Pre-allocate asset memory pools
        assetManager.mallocTexturePool(500);
        assetManager.mallocLazyLoadPool(100);

        // Register scenes into the engine registry (Supported by OlenEngineSupport IDE Plugin)
        registerScene("MAIN_MENU", new MainMenuScene());
        registerScene("GAMEPLAY", new GameplayScene());

        // Set entry scene by registered scene key
        init.setInitScene("MAIN_MENU");
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

### Creating a Scene

```java
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.scene.Atlas;
import com.fw.main.utils.platform.system.scene.Bgm;
import com.fw.main.utils.platform.system.scene.Scene;
import com.fw.main.utils.platform.system.scene.Sprite;

import java.awt.*;
import java.util.Properties;

public class MainMenuScene extends Scene {

    private Sprite titleLogo;
    private Bgm menuMusic;

    @Override
    public void init(SceneInit init) throws Exception {
        // Build scene-level texture atlas
        init.createAtlas("MENU_ATLAS", 2, binder -> {
            titleLogo = binder.registerSprite(IoUtils.getGameResourceStream("logo.png"), "spr_logo");
        });

        // Register scene-scoped BGM (automatically stopped and freed on scene dispose)
        menuMusic = init.registerMusic(IoUtils.getGameResourceStream("menu_theme.ogg"), true);

        // Enable automatic scene persistence
        this.saveable = true;
    }

    @Override
    protected void onEnter() {
        if (menuMusic != null) {
            menuMusic.getMusic().play(true, 0.8);
        }
    }

    @Override
    public void update(double dt) {
        // Scene logic update
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 1920, 1080);

        if (titleLogo != null) {
            titleLogo.draw(g, 760, 200, 400, 200);
        }
    }

    @Override
    public void mClicked(FwMouseAPI e) {
        if (e.isLeftButton()) {
            // Scene key checked and refactored by the IDE plugin
            base.changeScene("GAMEPLAY");
        }
    }

    @Override
    protected void save(Properties props) {
        props.setProperty("high_score", "15400");
    }

    @Override
    protected void load(Properties props) {
        String highScore = props.getProperty("high_score", "0");
    }
}
```

---

## Resource & Scene Key System

The engine and `OlenEngineSupport` IDE plugin identify key types by parsing designated method invocations and parameter argument positions.

### 1. Scene Keys (SCENE)
Scene identifiers used for transitions and scene bootstrapping are verified statically.

```java
// Registration
registerScene("SCENE_TITLE", new TitleScene());

// Initial Scene Setup
baseInit.setInitScene("SCENE_TITLE");

// Runtime Transition
base.changeScene("SCENE_TITLE");
```

### 2. Texture (TEXTURE)
Supports boot registration via proxy, synchronous/lazy runtime loading, direct bitmap injection, and retrieval.

```java
// Boot registration (returns a BootTextureProxy handle)
Texture bootTex = assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// Runtime loading
assetManager.loadTexture(AssetManager.LoadMode.SYNC, "tex_stage_bg", IoUtils.getGameResourceStream("bg.png"), null);
assetManager.loadTexture(AssetManager.LoadMode.LAZY, "tex_heavy_bg", IoUtils.getGameResourceStream("heavy.png"), "EVENT_BG_READY");

// Direct bitmap registration
assetManager.registerDirectTexture("tex_procedural", generatedBufferedImage);

// Instance retrieval
Texture tex = assetManager.getTexture("tex_player");
VolatileImage vImg = tex.getVolatileImage();
```

### 3. Sound Effects (SOUND)
Handles memory-resident sound effect buffers.

```java
// Boot registration
SoundAsset bootSnd = assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// Runtime loading
assetManager.loadSound(AssetManager.LoadMode.SYNC, "snd_jump", IoUtils.getGameResourceStream("jump.wav"), null);

// Playback
Sound snd = assetManager.getSound("snd_hit");
snd.play(1.0, 0.0); // volume: 1.0, pan: center (0.0)
```

### 4. Music (MUSIC)
Manages memory-resident or disk-streamed background audio tracks.

```java
// Boot registration
MusicAsset bootMusic = assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// Runtime loading
assetManager.loadMusic(AssetManager.LoadMode.SYNC, AssetManager.MusicType.STREAM_MUSIC, "bgm_stage1", IoUtils.getGameResourceStream("stage1.ogg"), null);

// Playback
Music music = assetManager.getMusic("bgm_main");
music.play(true, 0.75, 0.0); // loop: true, volume: 0.75, pan: center
```

### 5. Sprite Atlas (SPRITE & ATLAS)
Builds single-texture sheets and queries UV coordinates.

```java
// Boot-time Atlas registration
assetInit.registerBootAtlas("GLOBAL_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("player_idle.png"), "spr_player_idle");
});

// Scene-level Atlas creation
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// Query UV coordinates
Atlas atlas = assetManager.getAtlas("GLOBAL_ATLAS");
float[] uv = atlas.getUV("spr_player_idle"); // [u1, v1, u2, v2]

// Draw sprite directly
Sprite sprite = atlas.getSprite("spr_player_idle");
sprite.draw(g2d, 100, 100, 64, 64);
```

### 6. Framework Context (FW)
Static cross-module registry on `com.fw.main.Fw`.

```java
import com.fw.main.Fw;

// Registration
Fw.add("MAIN_ENGINE", this);
Fw.add(1, this);

// Usage
Base engine = Fw.get("MAIN_ENGINE");
```

### 7. Asynchronous Events (EVENT)
Handles event-driven loading milestones and lazy asset dispatching.

```java
// Bind event key
sceneInit.bindLazyEvent("EVENT_CHAPTER_1_ASSETS");

// Dispatch tasks
assetManager.event("EVENT_CHAPTER_1_ASSETS");

// Dispatch and await completion synchronously
assetManager.eventAndAwait("EVENT_CHAPTER_1_ASSETS");
```

### 8. Explicit Resource Disposal (Free)
Releases resources from active lookup tables and recycles memory handles.

```java
assetManager.free(AssetManager.AssetType.TEXTURE, "tex_stage_bg");
assetManager.free(AssetManager.AssetType.SOUND, "snd_jump");
assetManager.free(AssetManager.AssetType.MUSIC, "bgm_stage1");
assetManager.free(AssetManager.AssetType.ATLAS, "GAME_ATLAS");
```

---

## Prohibited Conflicting Method Declarations

To ensure high indexing performance during Dumb Mode, the IDE plugin identifies keys based on **method names and parameter positions (indices)**.

Avoid defining custom methods with the following signatures in your game classes to prevent false inspections, invalid autocompletion, or unintentional refactoring:

### Conflict-Prone Method Signatures

1. **Resource Registration Methods**
    * `registerBootTexture(String key, ...)`: 1st argument (index 0) recognized as TEXTURE key
    * `registerBootSound(String key, ...)`: 1st argument (index 0) recognized as SOUND key
    * `registerBootMusic(String key, ...)`: 1st argument (index 0) recognized as MUSIC key
    * `registerDirectTexture(String key, ...)`: 1st argument (index 0) recognized as TEXTURE key
    * `registerSprite(..., String key)`: 2nd argument (index 1) recognized as SPRITE key
    * `registerScene(String key, ...)`: 1st argument (index 0) recognized as SCENE key
    * `loadTexture(..., String key, ..., String eventKey)`: 2nd argument (index 1) recognized as TEXTURE, 4th argument (index 3) as EVENT key
    * `loadSound(..., String key, ..., String eventKey)`: 2nd argument (index 1) recognized as SOUND, 4th argument (index 3) as EVENT key
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3rd argument (index 2) recognized as MUSIC, 5th argument (index 4) as EVENT key

2. **Resource Lookup & Lifecycle Methods**
    * `getTexture(String key)`: 1st argument (index 0) recognized as TEXTURE key
    * `getSound(String key)`: 1st argument (index 0) recognized as SOUND key
    * `getMusic(String key)`: 1st argument (index 0) recognized as MUSIC key
    * `getAtlas(String key)`: 1st argument (index 0) recognized as ATLAS key
    * `getUV(String key)`: 1st argument (index 0) recognized as SPRITE key
    * `getSprite(String key)`: 1st argument (index 0) recognized as SPRITE key
    * `changeScene(String key)`: 1st argument (index 0) recognized as SCENE key
    * `setInitScene(String key)`: 1st argument (index 0) recognized as SCENE key
    * `event(String key)`: 1st argument (index 0) recognized as EVENT key
    * `eventAndAwait(String key)`: 1st argument (index 0) recognized as EVENT key
    * `bindLazyEvent(String key)`: 1st argument (index 0) recognized as EVENT key
    * `free(AssetType type, String key)` / `free(String type, String key)`: 2nd argument (index 1) mapped according to target asset type

---

## Developer Tooling & Diagnostic Systems

### 1. In-Game Developer Console (`~` Key)
The engine includes a full-featured Quake-style developer console with live syntax validation and tree autocompletion:
- **Navigation**: Use `Page Up` / `Page Down` to scroll logs, `Up` / `Down` to navigate suggestions or command history, and `Tab` to autocomplete.
- **Tree Autocompletion**: Integrated with live engine data (scene names, active texture/sound keys, quickput keys).
- **Console Built-in Commands**:
    * `sys scene <sceneKey>`: Live runtime scene transition.
    * `sys debug asset texture <key> <desktop|projectFolder|exeFolder>`: Export a loaded texture directly to PNG.
    * `sys debug asset atlas <key> <desktop|projectFolder|exeFolder>`: Export a packed atlas texture sheet to PNG.
    * `sys debug asset sound <key>` / `sys debug asset music <key> <true|false>`: Test and control audio playback live.
    * `sys quickput put <key> "<command>"`: Register custom command macros/aliases.
    * `sys exe performanceReader`: Open the telemetry performance reader window.
    * `sys exe settingEditor`: Open the runtime settings editor window.
    * `sys mem` / `sys thread`: Print heap consumption and active threads.
    * `sys dump`: Dump console buffer to `~/.{projectName}/logs/console_{timestamp}.txt`.

### 2. Embedded Performance Telemetry (`PerformanceRecorder` & `PerformanceReader`)
Configure telemetry recording via `Base.Builder.setPerformanceRecorderOption()`:
```java
builder.setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01");
```
- Telemetry captures CPU usage, Heap / Non-Heap memory, GC pauses, frame timing, and separate CPU/GPU work times along with hardware system info.
- Exported `.fwD` profile files can be visualized interactively using `PerformanceReader`:
    * Drag **Left Mouse** to pan across frames.
    * Drag **Right Mouse Up/Down** to adjust display scale cap.
    * **Mouse Wheel** to zoom in and out.
    * Click named phase buttons to view phase-specific Max, Min, and Average statistics.

### 3. Setting Management & Editor GUI (`EngineSettingEditor`)
Engine and runtime preferences are persisted as separate property files under `~/.{projectName}/EngineSettings/`.
- Launch the GUI editor directly via `new EngineSettingEditor().setVisible(true);` or console command `sys exe settingEditor`.
- Features real-time editing of display resolution, fullscreen mode, wait modes, thread modes, CRT jitter, buffer sizing, and a complete directory reset option.

---

## Recommended VM Options

- Force DirectX (Windows D3D pipeline)
```
-Dsun.java2d.d3d=true
```

- Force OpenGL (Linux / macOS pipeline)
```
-Dsun.java2d.opengl=true
```

- Force OpenGL Framebuffer Objects (FBO)
```
-Dsun.java2d.opengl.fbobject=true
```

- Disable Legacy DirectDraw
```
-Dsun.java2d.noddraw=true
```

- Reset VRAM Acceleration Caching Threshold
```
-Dsun.java2d.accthreshold=0
```

- Enable ZGC (Sub-millisecond Latency Garbage Collector)
```
-XX:+UseZGC
```

- Enable Generational ZGC (JDK 21+)
```
-XX:+ZGenerational
```

---

## Life Cycle

1. **Bootstrapping (Main Thread)**:
    * Resolves `Config` properties and persistent configurations (`EngineSettings`).
    * Instantiates the `Base` display window, initial input hooks, and graphics buffer strategies.
2. **Asynchronous Preload (`Async-Loader` Thread)**:
    * Executes `BaseInit` and `onAssetInit`.
    * Pre-allocates memory pools, binds boot textures, sounds, musics, and bakes boot atlases.
    * Loads framework data (`io.load`) and primes the initial scene pipeline.
3. **Execution Loop**:
    * **Single-Thread Mode (`SINGLE`, Default)**: Unified `mainLoop` thread drives scene updates (`stepEngineUpdate(dt)`) and graphics presentation (`renderLoop()`) with precise timing sync (`WaitMode`).
    * **Dual-Thread Mode (`DUAL`, Deprecated)**: Splits logic updates (`logicThread`) and rendering passes (`renderThread`) into parallel execution threads.
4. **Scene Lifecycle & Transitions**:
    * Triggered via `changeScene(key)`.
    * Active scene begins `TRANSITION_FADE_OUT` -> runs `onExit()`, `dispose()`, and releases scene-bound audio and atlases.
    * Next scene initialized on worker thread (`init(sceneInit)`) -> assets loaded/baked -> `onEnter()` triggered -> `TRANSITION_FADE_IN`.
5. **Safe Shutdown Pipeline**:
    * Intercepts window closing events.
    * Awaits active loop termination within a grace timeout (`shutdownTimeoutMs`).
    * Synchronizes pending game and scene save data (`save()`), executes exit operator hooks, restores native display resolution, and frees graphics hardware surfaces.

---

## Known Issues & Tips

- **Dual-Thread Mode Deprecation**: `ThreadMode.DUAL` is marked as `@Deprecated`. In modern JVMs and AWT pipelines, `ThreadMode.SINGLE` paired with `WaitMode.HYBRID` or `WaitMode.OS_SLEEP` yields superior frame consistency and eliminates inter-thread display tearing.
- **macOS Window Resizing**: When resizing windows rapidly on macOS with the `DEFAULT` rendering option, AWT can occasionally suffer internal pipeline crashes. If targeted for macOS distribution, configure `RenderingOption.LEGACY`.
- **Safe Runtime Fallback**: When `Config.Builder.setSafeRuntime(true)` is enabled, missing or corrupt image/audio resources will not throw fatal startup exceptions. Instead, the engine automatically substitutes a visible fallback texture (`WrongTexture.png`), fallback warning sounds, and dynamically expands internal object pools with warnings logged to the developer console.
- **Undocumented Implementation & Source Code Reference**: Because some internal APIs, helper routines, and low-level subsystem hooks may lack comprehensive documentation, the complete engine source code is bundled directly as an attached PDF in the release assets. For implementation inquiries, architectural lookups, or troubleshooting subtle runtime interactions, feed the complete source PDF directly into an LLM context as a reference manual to get exact, context-aware code solutions.

---
---

# Olen Engine (PRE 0.4.0) - 한국어 (Korean)

Java/AWT 기반의 경량 2D 게임 엔진입니다. 고성능 메모리 관리, 동적 텍스처 아틀라스 패킹, 완성된 한글 조합 입력 모듈, 리소스 및 씬 문자열 키에 대한 정적 분석/IDE 지원, 트리 기반 자동완성을 지원하는 대화형 개발자 콘솔, 내장 성능 프로파일링 및 텔레메트리 스위트, 매끄러운 화면 전환을 지원하는 견고한 씬 라이프사이클을 제공합니다.

---

# 변경 로그 (CHANGE LOG)

## PRE 0.4.0
- **씬 아키텍처 및 화면 전환 파이프라인**
    * `Scene`을 완전한 라이프사이클 훅을 갖춘 확장형 추상 클래스로 리팩토링: `init(SceneInit)`, `update(dt)`, `render(Graphics2D)`, `onEnter()`, `onExit()`, `onDispose()`
    * 페이드 속도(`setFadeSpeed`) 및 상태 제어(`TransitionPhase`: `TRANSITION_FADE_OUT`, `TRANSITION_LOADING`, `TRANSITION_FADE_IN`)가 가능한 비동기 씬 전환 파이프라인 추가
    * `LoadingRenderer` 인터페이스를 통한 커스텀 로딩 화면 시각화 훅 지원
    * `~/.{projectName}/save/{sceneName}.properties` 경로에 상태를 저장하는 씬 전용 영속성 API 추가: `saveable`, `save(Properties)`, `load(Properties)`
    * 씬 레지스트리 통합(`registerScene`, `changeScene`) 및 씬 범위 오디오 수명 관리(`Sfx`, `Bgm`)
- **IDE 플러그인(`OlenEngineSupport`) 씬 키 지원**
    * 정적 인덱싱 및 코드 지원을 씬 식별 키(`SCENE`)로 확장: `changeScene("")`, `setInitScene("")`에 대한 자동완성, 선언부 이동, 에러 검사, 리팩토링 지원
- **에셋 파이프라인 및 동시성 개선**
    * `loadTexture`, `loadSound`, `loadMusic` 전반에 걸친 통합 `LoadMode`(`SYNC`, `LAZY`) 적용
    * 래치 기반 동기화(`awaitCompletion`, `awaitCompletionOrThrow`), 작업 취소 및 풀 재활용을 지원하는 `DynamicAssetObject` 도입
    * 프록시 핸들(`BootTextureProxy`, `BootSoundProxy`, `BootMusicProxy`)을 반환하는 `AssetInit` 부트 에셋 등록 및 부트 타임 아틀라스 사전 베이킹(`registerBootAtlas`) 지원
    * 비트맵 직접 등록 API 추가: `registerDirectTexture(assetKey, BufferedImage)`
    * 리소스 해제(`free`) 대상을 기존 `TEXTURE`, `SOUND`, `MUSIC` 외에 `ATLAS`까지 확장
- **그래픽 파이프라인 및 디스플레이 제어**
    * 다중 렌더링 아키텍처 도입: `DrawMode.DIRECT` 및 오프스크린 `VolatileImage` 스케일링 기반의 `DrawMode.BUFFER`
    * `RenderingOption`에 `LEGACY`(macOS 창 크기 조절 안정성 대응) 및 고도화된 `CRT` 옵션 확장
    * CRT 시뮬레이션 고도화: 64슬라이스 듀얼 패스 배럴 왜곡 곡률(`CURVE_X`, `CURVE_Y`), CRT 지터 시뮬레이션(`crtJitterEnabled`), 스캔라인, 형광체 마스크, 소프트 비네팅, 동적 펄스 깜빡임
    * 디스플레이 제어: 독점 전체화면 모드(FSEM), 해상도 및 화면 주사율 동적 전환(`setDisplayResolution`), 크기 설정이 가능한 엔진 하드웨어 커서(`useEngineCursor`, `setCursorSize`)
    * 고DPI OS 스케일 보정을 포함한 정수 단위 물리 스케일링 스냅 연산(`ViewMetrics`, `sanitizeDeviceScale`)
    * CRT 왜곡 곡률 좌표를 역보정하는 마우스 입력 좌표계 매핑(`updateVirtualMouseCrt`)
- **개발자 콘솔 및 커맨드 시스템**
    * 모듈형 트리 기반 자동완성 엔진(`AutoCompleteManager`, `TreeBuilder`, `CandidateRule`, `Condition`) 구축
    * 이원화된 제안 인터페이스: 좌측 전체 후보군 표시, 우측 매칭 추천 목록 및 방향키 탐색
    * 실시간 구문 및 공백 경고 표시 (따옴표 미닫힘, 연속 공백 감지)
    * 내장 `sys` 시스템 커맨드 네임스페이스:
        - `sys scene <sceneKey>`: 런타임 실시간 씬 전환
        - `sys debug asset <texture|sound|music|atlas> <key> [desktop|projectFolder|exeFolder]`: 에셋 즉시 검사 및 PNG 덤프
        - `sys quickput <put|delete|clear>`: 자동 갱신 트리 및 디스크 저장을 지원하는 매크로/단축어 시스템
        - `sys exe <performanceReader|settingEditor|testInConsole>`: 내장 진단 도구 콘솔 실행
        - `sys mem`, `sys thread`, `sys dump`, `sys getInfo`: 실시간 JVM 힙, 스레드 덤프 및 시스템 정보 진단
- **진단 도구 및 성능 프로파일링**
    * 독립 실행형 시각화 GUI: 다중 지표 그래프 탭(`GraphTab`), 팬/줌 인터랙션, 동적 캡 조절, 페이즈 구간 마커 및 틱별 지표를 지원하는 `PerformanceReader`
    * 종합 텔레메트리 수집기(`PerformanceRecorder`): CPU 사용률, JVM 힙/논힙 메모리, GC 소요 시간, 프레임 타임, 로직/렌더 작업 시간, OS 아키텍처, 하드웨어 스펙, 네트워크 어댑터, 디스크 볼륨 정보
    * 런타임 설정 GUI: 디스크의 개별 엔진 설정을 실시간으로 편집, 재로드, 검증하고 전체 설정을 초기화할 수 있는 `EngineSettingEditor`
- **안정성 및 결함 감내 (Fault Tolerance)**
    * 스레드별 예외 억제기(`ErrorTracker`): 동일 프레임 연속 예외를 필터링하여 콘솔 및 표준 출력 도배 방지
    * 런타임 및 에셋 로딩 예외를 즉각 안내하는 온스크린 알림 HUD(`ErrorBoxManager`)

## PRE 0.2.0
- 리소스 키 정적 인덱싱 및 IntelliJ IDEA 전용 플러그인(`OlenEngineSupport`) 연동
    * 리소스 문자열 키(`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`) 자동완성, 선언부 이동, 실시간 에러 검사, 일괄 이름 변경(Refactoring)
- 표준화된 리소스 관리 API 및 에셋 파이프라인
    * 비동기 에셋 로딩 완료 이벤트 키(`EVENT`) 연동
    * 명시적 리소스 해제 API: `free(TYPE, key)`
    * 글로벌 프레임워크 컨텍스트 전용 키 인덱싱: `com.fw.main.Fw`
    * 로드 실패 시 충돌을 방지하는 대체 텍스처(`wrongTexture`) 및 에셋 풀 자동 확장 지원 `Safe Runtime` 모드
- 안정성 및 라이프사이클 강화
    * 씬 전환 및 엔진 종료 시 발생 가능한 레이스 컨디션 제거
- 렌더링 파이프라인 및 디스플레이 제어
    * CRT 셰이더 및 시뮬레이션 추가 (`RenderingOption.CRT`)
    * 독점 전체화면 모드(FSEM) 및 동적 디스플레이 모드 전환 지원 (해상도, 비트 심도, 주사율)
    * 가상 화면 해상도 설정 지원 (`virtualScreenWidth`, `virtualScreenHeight`)
    * 엔진 전용 커스텀 하드웨어 커서(`EngineCursor.png`) 지원
    * 실험적 드로우콜 캐싱 시스템 제거 (`RenderingOption.EXPERIMENTAL`, `Call`, `LazyCall`, `StaticCall`)
- 프레임 타이밍 제어
    * 정밀 프레임 동기화 대기 모드 도입 (`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`)
- 입력 및 디버깅/콘솔 시스템 개선
    * `KeyBindingBase` 입력 처리 최적화: Swing `ActionMap` 오버헤드 제거, 경량 65,536 정적 키 테이블 인덱싱 전환, 바인딩 반전 버그 수정
    * 반응형 콘솔 UI 및 자동완성 탐색 개선: 화면 해상도 연동 UI 스케일링, 방향키(UP/DOWN) 제안 목록 탐색
    * 상시 온스크린 디버그 HUD 추가

## PRE 0.1.0
- 초기화 아키텍처 개선
    * 기본 생성자 기반 초기화에서 명시적 초기화(`init`) 단계로 전환
    * `AssetInit` 부트스트래핑 중 프록시 객체 반환 지원
- 성능 프로파일링
    * `PerformanceRecorder` 및 `PerformanceReader` 추가
- 사운드 시스템
    * 오디오 엔진을 `TinySound`로 롤백

---

## IntelliJ IDEA 플러그인 가이드

엔진 내부의 다양한 리소스와 씬(텍스처, 사운드, 스프라이트, 씬 등)은 문자열 키로 식별됩니다. 오타를 방지하고 원활한 코드 내비게이션을 위해 전용 플러그인 설치를 강력히 권장합니다.

- 저장소: https://github.com/yooncrow33/OlenEnginePlugin

### 플러그인 주요 기능
1. **자동완성 (Auto-completion)**: `getTexture("")`, `changeScene("")`, `setInitScene("")` 등의 메서드 호출 시 등록된 키 팝업 목록 제공
2. **선언부 이동 (Ctrl + Click)**: 사용처 문자열 리터럴에서 등록 선언부(예: `registerBootTexture`, `registerScene`)로 즉시 이동
3. **구문 / 에러 검사 (Syntax Inspection)**: 등록되지 않았거나 오타가 발생한 키에 대해 실시간 경고 하이라이트(붉은 밑줄) 표시
4. **이름 변경 리팩토링 (Shift + F6)**: 키 이름 변경 시 등록 선언부와 프로젝트 전체의 사용처를 동시 수정

### 공지사항
- 테스트 환경: #IC-252.26199.169 전용.

---

## 주요 기능 (Key Features)

- **고급 그래픽 및 렌더링 파이프라인**
    * `VolatileImage` 및 설정 가능한 버퍼 개수를 지원하는 `BufferStrategy` 기반 하드웨어 이중 버퍼링
    * 멀티 렌더 모드 지원: 직접 그리기(`DrawMode.DIRECT`) 또는 중간 오프스크린 버퍼 스케일링(`DrawMode.BUFFER`)
    * MaxRects 빈 패킹 알고리즘 및 투명 여백 자동 알파 트림(Alpha-trim)을 적용한 동적 텍스처 아틀라스 생성
    * 고DPI OS 배율 보정이 포함된 부동소수점 및 정수 스냅 물리 스케일링 계산(`ViewMetrics`)
    * macOS 호환성을 위한 레거시 렌더링 및 스캔라인, 유리 반사, 지터가 구현된 다중 슬라이스 CRT 배럴 왜곡 시뮬레이션

- **씬 관리 및 화면 전환 아키텍처**
    * 명확한 상태 분리를 지원하는 일급 객체 수준의 `Scene` 라이프사이클 관리
    * 페이드 속도 조정 및 임의의 로딩 화면 훅(`LoadingRenderer`)을 지원하는 내장 비동기 전환 파이프라인
    * 사용자 애플리케이션 데이터 폴더에 자동으로 기록되는 씬 단위 데이터 직렬화 훅(`saveable`, `save`, `load`)

- **고성능 오디오 엔진 (TinySound 1.1.1)**
    * 서브 스레드 기반 44.1kHz 16-bit Signed PCM 저지연 오디오 믹서
    * 메모리 상주 재생(`MemSound`, `MemMusic`) 및 디스크 스트리밍 재생(`StreamMusic`, `StreamSound`)
    * 볼륨 조절, 좌우 패닝(`[-1.0, 1.0]`), 샘플 프레임 및 초 단위 루프 지점 설정, 마스터 볼륨 제어 지원

- **개발자 환경 및 도구**
    * 트리 구조 자동완성 엔진과 입력 구문 검증이 탑재된 인게임 개발자 콘솔(`~` 키)
    * 클립보드 붙여넣기 및 시각적 커서 렌더링을 지원하는 AWT 환경 한글 완성형 입력 모듈(`TextModule`, `TextObject`)
    * 텔레메트리 덤프(`.fwD`)를 생성하는 `PerformanceRecorder` 및 대화형 시각화 GUI인 `PerformanceReader`
    * `~/.{projectName}/EngineSettings/` 하위에 프로퍼티 파일 단위로 설정을 저장하는 전용 설정 편집기 GUI(`EngineSettingEditor`)
    * 예외 도배 방지 필터링 및 시각적 온스크린 에러 HUD 알림 배너(`ErrorBoxManager`)

---

## 요구 사양 (Requirements)

- **JDK:** OpenJDK 21+ (GraalVM 21+ 권장)
- **OS:** Windows / macOS / Linux

---

## 시작하기 (Getting Started)

```java
import com.fw.main.*;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.AssetManager;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
import com.fw.main.utils.platform.system.scene.Scene;

import java.awt.*;

public class MyGame extends Base {

    public MyGame() {
        super(new Base.Builder()
                // Base.Builder 내부에서 Core 설정을 직접 체이닝하여 초기화
                .setCoreConfig(new Config.Builder("MyGameFolder")
                        .setWindowWidth(1280) // 초기 창 너비
                        .setWindowHeight(720) // 초기 창 높이
                        .setUseKoreanModule(true) // 한글 조합 입력 모듈 활성화
                        .setUseIntegerPhysicalScaling(true) // 물리 스케일링 정수 스냅 적용
                        .setUseEncryption(false) // 세이브 파일 암호화 여부
                        .setSafeRuntime(true) // 에셋 누락 시 크래시 대신 폴백 사용
                )
                // 창 및 디스플레이 옵션
                .setTitle("My Olen Game")
                .setFullScreen(false)
                .setDisplayResolution(1920, 1080, DisplayMode.BIT_DEPTH_MULTI, DisplayMode.REFRESH_RATE_UNKNOWN)
                .setVirtualScreenWidth(1920) // 논리적 가상 렌더링 너비
                .setVirtualScreenHeight(1080) // 논리적 가상 렌더링 높이
                .setUseEngineCursor(true) // 엔진 커스텀 하드웨어 커서 사용
                .setCursorSize(32, 32)
                
                // 파이프라인 및 실행 흐름 옵션
                .setRenderingOption(RenderingOption.DEFAULT) // DEFAULT, LEGACY, CRT
                .setDrawMode(Base.DrawMode.DIRECT) // DIRECT 또는 BUFFER
                .setBufferSize(1920, 1080) // BUFFER 모드용 오프스크린 버퍼 크기
                .setWaitMode(Base.WaitMode.HYBRID) // OS_SLEEP, HYBRID, BUSY_WAIT
                .setThreadMode(Base.ThreadMode.SINGLE) // SINGLE (권장) 또는 DUAL
                .setCrtJitterEnabled(true) // CRT 모드 스캔라인 지터 활성화
                
                // 화면 전환 및 타이밍 매개변수
                .setFadeSpeed(3.0f) // 씬 크로스페이드 속도
                .setLoadingRenderer((g, progress, message, w, h) -> {
                    // 커스텀 로딩 화면 렌더링 훅
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                    g.setColor(Color.WHITE);
                    g.drawString(message + " (" + (int)(progress * 100) + "%)", w / 2 - 100, h / 2);
                })
                .setResizeSettleNanos(150_000_000L) // 창 리사이즈 시 스왑체인 안정화 대기 시간
                .setShutdownTimeoutMs(2000) // 엔진 종료 시 스레드 join 대기 타임아웃
                .setBufferStrategyCount(2) // 더블 버퍼링
                .setRenderRetryCount(3) // 서피스 손실 복구 재시도 횟수
                .setCloseWindowWithKillVM(true) // 창 닫기 시 프로세스 완전 종료
                
                // 진단 도구 및 컨텍스트 바인딩
                .setUseConsole(true) // 개발자 콘솔 활성화 (~ 키)
                .setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01")
                .setStringKey("MAIN_GAME_INSTANCE")
                .setIntegerKey(1)
        );
    }

    @Override
    public void init(BaseInit init) {
        // 에셋 메모리 풀 용량 할당
        assetManager.mallocTexturePool(500);
        assetManager.mallocLazyLoadPool(100);

        // 씬 등록 (OlenEngineSupport IDE 플러그인이 키 정적 검사 지원)
        registerScene("MAIN_MENU", new MainMenuScene());
        registerScene("GAMEPLAY", new GameplayScene());

        // 시작 씬 설정 (등록된 씬 키 전달)
        init.setInitScene("MAIN_MENU");
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

### 씬 작성 예시 (Scene)

```java
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.scene.Atlas;
import com.fw.main.utils.platform.system.scene.Bgm;
import com.fw.main.utils.platform.system.scene.Scene;
import com.fw.main.utils.platform.system.scene.Sprite;

import java.awt.*;
import java.util.Properties;

public class MainMenuScene extends Scene {

    private Sprite titleLogo;
    private Bgm menuMusic;

    @Override
    public void init(SceneInit init) throws Exception {
        // 씬 전용 텍스처 아틀라스 생성
        init.createAtlas("MENU_ATLAS", 2, binder -> {
            titleLogo = binder.registerSprite(IoUtils.getGameResourceStream("logo.png"), "spr_logo");
        });

        // 씬 스코프 BGM 등록 (씬 파괴 시 자동으로 정지 및 메모리 해제)
        menuMusic = init.registerMusic(IoUtils.getGameResourceStream("menu_theme.ogg"), true);

        // 씬 자동 영속성 활성화
        this.saveable = true;
    }

    @Override
    protected void onEnter() {
        if (menuMusic != null) {
            menuMusic.getMusic().play(true, 0.8);
        }
    }

    @Override
    public void update(double dt) {
        // 씬 로직 갱신 루프
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 1920, 1080);

        if (titleLogo != null) {
            titleLogo.draw(g, 760, 200, 400, 200);
        }
    }

    @Override
    public void mClicked(FwMouseAPI e) {
        if (e.isLeftButton()) {
            // 플러그인에 의해 키 유효성이 검사되고 리팩토링되는 씬 전환 호출
            base.changeScene("GAMEPLAY");
        }
    }

    @Override
    protected void save(Properties props) {
        props.setProperty("high_score", "15400");
    }

    @Override
    protected void load(Properties props) {
        String highScore = props.getProperty("high_score", "0");
    }
}
```

---

## 리소스 및 씬 키 시스템 (Resource & Scene Key System)

엔진과 `OlenEngineSupport` IDE 플러그인은 메서드 이름과 파라미터 인덱스 위치를 파싱하여 키의 종류를 판별합니다.

### 1. 씬 키 (SCENE)
씬 전환 및 부트스트래핑에 사용되는 씬 식별자 키는 플러그인에 의해 정적으로 추적됩니다.

```java
// 등록 (1번째 인자: Scene 키)
registerScene("SCENE_TITLE", new TitleScene());

// 시작 씬 지정
baseInit.setInitScene("SCENE_TITLE");

// 런타임 씬 전환
base.changeScene("SCENE_TITLE");
```

### 2. 텍스처 (TEXTURE)
프록시 기반 부트 등록, 동기/비동기 런타임 로딩, 직접 비트맵 주입 및 인스턴스 조회를 지원합니다.

```java
// 부트 타임 등록 (BootTextureProxy 핸들 즉시 반환)
Texture bootTex = assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// 런타임 로딩 (SYNC는 즉시 로드, LAZY는 DynamicAssetObject 백그라운드 로드)
assetManager.loadTexture(AssetManager.LoadMode.SYNC, "tex_stage_bg", IoUtils.getGameResourceStream("bg.png"), null);
assetManager.loadTexture(AssetManager.LoadMode.LAZY, "tex_heavy_bg", IoUtils.getGameResourceStream("heavy.png"), "EVENT_BG_READY");

// 직접 비트맵 등록
assetManager.registerDirectTexture("tex_procedural", generatedBufferedImage);

// 인스턴스 조회
Texture tex = assetManager.getTexture("tex_player");
VolatileImage vImg = tex.getVolatileImage();
```

### 3. 효과음 (SOUND)
메모리 상주형 단문 오디오 버퍼를 처리합니다.

```java
// 부트 타임 등록
SoundAsset bootSnd = assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// 런타임 로딩
assetManager.loadSound(AssetManager.LoadMode.SYNC, "snd_jump", IoUtils.getGameResourceStream("jump.wav"), null);

// 재생
Sound snd = assetManager.getSound("snd_hit");
snd.play(1.0, 0.0); // 볼륨: 1.0, 팬: 중앙 (0.0)
```

### 4. 배경음악 (MUSIC)
메모리 로드형 또는 디스크 스트리밍 BGM 리소스를 관리합니다.

```java
// 부트 타임 등록
MusicAsset bootMusic = assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// 런타임 스트리밍 로딩
assetManager.loadMusic(AssetManager.LoadMode.SYNC, AssetManager.MusicType.STREAM_MUSIC, "bgm_stage1", IoUtils.getGameResourceStream("stage1.ogg"), null);

// 재생
Music music = assetManager.getMusic("bgm_main");
music.play(true, 0.75, 0.0); // 반복: true, 볼륨: 0.75, 팬: 중앙
```

### 5. 스프라이트 아틀라스 (SPRITE & ATLAS)
개별 이미지를 아틀라스 시트로 패킹하고 UV 좌표를 조회합니다.

```java
// 부트 타임 아틀라스 등록
assetInit.registerBootAtlas("GLOBAL_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("player_idle.png"), "spr_player_idle");
});

// 씬 레벨 아틀라스 생성
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// UV 좌표 조회
Atlas atlas = assetManager.getAtlas("GLOBAL_ATLAS");
float[] uv = atlas.getUV("spr_player_idle"); // [u1, v1, u2, v2]

// 알파 트림 오프셋이 자동 보정된 스프라이트 렌더링
Sprite sprite = atlas.getSprite("spr_player_idle");
sprite.draw(g2d, 100, 100, 64, 64);
```

### 6. 프레임워크 베이스 컨텍스트 (FW)
`com.fw.main.Fw` 기반의 전역 인스턴스 저장소입니다.

```java
import com.fw.main.Fw;

// 등록
Fw.add("MAIN_ENGINE", this);
Fw.add(1, this);

// 조회
Base engine = Fw.get("MAIN_ENGINE");
```

### 7. 비동기 완료 이벤트 (EVENT)
비동기 로딩 완료 시점 추적 및 지연 에셋 일괄 디스패치에 사용됩니다.

```java
// 이벤트 키 바인딩
sceneInit.bindLazyEvent("EVENT_CHAPTER_1_ASSETS");

// 바인딩된 지연 에셋 비동기 작업 발행
assetManager.event("EVENT_CHAPTER_1_ASSETS");

// 작업을 발행하고 완료될 때까지 현재 스레드 동기 대기
assetManager.eventAndAwait("EVENT_CHAPTER_1_ASSETS");
```

### 8. 명시적 리소스 메모리 해제 (Free)
활성 테이블에서 리소스를 해제하고 내부 풀 핸들을 정리합니다.

```java
assetManager.free(AssetManager.AssetType.TEXTURE, "tex_stage_bg");
assetManager.free(AssetManager.AssetType.SOUND, "snd_jump");
assetManager.free(AssetManager.AssetType.MUSIC, "bgm_stage1");
assetManager.free(AssetManager.AssetType.ATLAS, "GAME_ATLAS");
```

---

## 중복 선언 금지 메서드 시그니처

인덱싱 단계(Dumb Mode)의 빠른 구문 분석 성능을 위해 IDE 플러그인은 리시버 타입을 역추적하지 않고 **메서드 이름과 파라미터 위치(Index)** 만으로 키를 식별합니다.

사용자 클래스에서 아래와 동일한 이름과 문자열 파라미터 배치를 가진 메서드를 정의할 경우, 일반 문자열이 엔진 키로 잘못 인식되어 허위 경고가 발생하거나 리팩토링 시 의도치 않게 변경될 수 있습니다.

### 충돌 주의 메서드 목록

1. **리소스 등록 메서드**
    * `registerBootTexture(String key, ...)`: 1번째 인자 (index 0) -> TEXTURE 키 인식
    * `registerBootSound(String key, ...)`: 1번째 인자 (index 0) -> SOUND 키 인식
    * `registerBootMusic(String key, ...)`: 1번째 인자 (index 0) -> MUSIC 키 인식
    * `registerDirectTexture(String key, ...)`: 1번째 인자 (index 0) -> TEXTURE 키 인식
    * `registerSprite(..., String key)`: 2번째 인자 (index 1) -> SPRITE 키 인식
    * `registerScene(String key, ...)`: 1번째 인자 (index 0) -> SCENE 키 인식
    * `loadTexture(..., String key, ..., String eventKey)`: 2번째 인자 (index 1) -> TEXTURE, 4번째 인자 (index 3) -> EVENT 키 인식
    * `loadSound(..., String key, ..., String eventKey)`: 2번째 인자 (index 1) -> SOUND, 4번째 인자 (index 3) -> EVENT 키 인식
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3번째 인자 (index 2) -> MUSIC, 5번째 인자 (index 4) -> EVENT 키 인식

2. **리소스 조회 및 수명 주기 메서드**
    * `getTexture(String key)`: 1번째 인자 (index 0) -> TEXTURE 키 인식
    * `getSound(String key)`: 1번째 인자 (index 0) -> SOUND 키 인식
    * `getMusic(String key)`: 1번째 인자 (index 0) -> MUSIC 키 인식
    * `getAtlas(String key)`: 1번째 인자 (index 0) -> ATLAS 키 인식
    * `getUV(String key)`: 1번째 인자 (index 0) -> SPRITE 키 인식
    * `getSprite(String key)`: 1번째 인자 (index 0) -> SPRITE 키 인식
    * `changeScene(String key)`: 1번째 인자 (index 0) -> SCENE 키 인식
    * `setInitScene(String key)`: 1번째 인자 (index 0) -> SCENE 키 인식
    * `event(String key)`: 1번째 인자 (index 0) -> EVENT 키 인식
    * `eventAndAwait(String key)`: 1번째 인자 (index 0) -> EVENT 키 인식
    * `bindLazyEvent(String key)`: 1번째 인자 (index 0) -> EVENT 키 인식
    * `free(AssetType type, String key)` / `free(String type, String key)`: 2번째 인자 (index 1) -> 지정된 에셋 타입에 대응되는 키로 인식

---

## 개발자 도구 및 진단 시스템

### 1. 인게임 개발자 콘솔 (`~` 키)
실시간 구문 유효성 검사 및 트리 자동완성을 지원하는 퀘이크 스타일 콘솔입니다:
- **조작**: `Page Up` / `Page Down`으로 로그 스크롤, `Up` / `Down`으로 추천 제안 및 커맨드 히스토리 탐색, `Tab`으로 완성.
- **트리 자동완성**: 활성화된 씬 이름, 텍스처/사운드 키, 사용자 단축어와 실시간 동기화.
- **주요 내장 명령어**:
    * `sys scene <sceneKey>`: 실행 중 실시간 씬 전환.
    * `sys debug asset texture <key> <desktop|projectFolder|exeFolder>`: 메모리의 텍스처를 지정 폴더에 PNG로 즉시 덤프.
    * `sys debug asset atlas <key> <desktop|projectFolder|exeFolder>`: 베이킹된 아틀라스 통 이미지를 PNG로 덤프.
    * `sys debug asset sound <key>` / `sys debug asset music <key> <true|false>`: 오디오 즉각 재생 및 제어.
    * `sys quickput put <key> "<command>"`: 사용자 정의 명령어 매크로 등록.
    * `sys exe performanceReader`: 성능 프로파일 뷰어 GUI 실행.
    * `sys exe settingEditor`: 엔진 설정 에디터 GUI 실행.
    * `sys mem` / `sys thread`: JVM 힙 상태 및 활성 스레드 목록 출력.
    * `sys dump`: 콘솔 버퍼를 `~/.{projectName}/logs/console_{timestamp}.txt`로 내보내기.

### 2. 내장 성능 텔레메트리 스위트 (`PerformanceRecorder` & `PerformanceReader`)
`Base.Builder.setPerformanceRecorderOption()`으로 텔레메트리 기록을 구성합니다:
```java
builder.setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01");
```
- CPU 점유율, 힙/논힙 메모리, GC 소요 시간, 프레임 타임, CPU/GPU 개별 작업 시간 및 세부 시스템 사양을 자동 수집합니다.
- 내보내진 `.fwD` 프로파일 파일은 `PerformanceReader`를 통해 인터랙티브하게 분석할 수 있습니다:
    * **마우스 좌클릭 드래그**: 타임라인 좌우 팬 이동.
    * **마우스 우클릭 상하 드래그**: 상단 캡(Y축 표시 상한선) 동적 조절.
    * **마우스 휠**: 시간축 줌 인/아웃.
    * 하단 페이즈 버튼 클릭: 구간별 최대/최소/평균 통계 표시.

### 3. 설정 관리 GUI (`EngineSettingEditor`)
엔진 설정은 `~/.{projectName}/EngineSettings/` 하위에 각 항목별 독립된 프로퍼티 파일로 영속화됩니다.
- 코드 상에서 `new EngineSettingEditor().setVisible(true);`를 호출하거나 콘솔에서 `sys exe settingEditor`를 입력하여 실행합니다.
- 해상도, 전체화면 여부, 대기 모드, 스레드 모드, CRT 지터, 버퍼 크기 등을 실시간으로 조정하고 즉시 저장하거나 디렉터리를 초기화할 수 있습니다.

---

## 권장 JVM 옵션 (Recommended VM Options)

- DirectX 가속 강제 (Windows D3D 파이프라인)
```
-Dsun.java2d.d3d=true
```

- OpenGL 가속 강제 (Linux / macOS 파이프라인)
```
-Dsun.java2d.opengl=true
```

- OpenGL Framebuffer Objects (FBO) 가속 강제
```
-Dsun.java2d.opengl.fbobject=true
```

- 구형 DirectDraw 비활성화
```
-Dsun.java2d.noddraw=true
```

- VRAM 캐시 가속 임계값 초기화
```
-Dsun.java2d.accthreshold=0
```

- ZGC 활성화 (밀리초 미만 지연시간 가비지 컬렉터)
```
-XX:+UseZGC
```

- 세대별 ZGC 활성화 (JDK 21+)
```
-XX:+ZGenerational
```

---

## 엔진 라이프사이클 (Life Cycle)

1. **부트스트래핑 (Main Thread)**:
    * `Config` 파라미터 및 `EngineSettings`의 저장된 속성을 로드하고 유효성을 검증합니다.
    * `Base` 윈도우 프레임을 구성하고 버퍼 전략과 초기 입력 라우터를 설정합니다.
2. **비동기 사전 로드 (`Async-Loader` 스레드)**:
    * `BaseInit` 및 `onAssetInit` 실행.
    * 풀 메모리를 사전 할당하고 부트 리소스 바인딩 및 부트 아틀라스를 베이킹합니다.
    * 세이브 데이터(`io.load`)를 읽고 초기 지정 씬 파이프라인을 구성합니다.
3. **루프 실행 단계**:
    * **단일 스레드 모드 (`SINGLE`, 권장/기본값)**: 메인 루프 스레드가 `stepEngineUpdate(dt)`와 `renderLoop()`을 정밀 타이밍 모드(`WaitMode`)에 맞춰 순차 구동합니다.
    * **이중 스레드 모드 (`DUAL`, 비권장/Deprecated)**: 로직 갱신(`logicThread`)과 화면 렌더링(`renderThread`)을 병렬 스레드로 분리 실행합니다.
4. **씬 라이프사이클 및 화면 전환**:
    * `changeScene(key)` 호출 시 작동 시작.
    * 현재 활성 씬이 `TRANSITION_FADE_OUT`을 거치며 `onExit()` 및 `dispose()`를 실행하여 리소스를 해제합니다.
    * 새 씬이 백그라운드 스레드에서 초기화(`init(sceneInit)`)되고 에셋 베이킹 완료 후 `onEnter()`가 호출되며 `TRANSITION_FADE_IN`으로 활성화됩니다.
5. **안전한 엔진 종료 (Safe Shutdown)**:
    * 창 닫기 이벤트를 감지하여 안전 종료 절차에 진입합니다.
    * 지정된 타임아웃(`shutdownTimeoutMs`) 내에 실행 중인 워커 스레드의 정상 종료를 대기(join)합니다.
    * 게임 및 활성 씬 데이터를 디스크에 플러시(`save()`)하고, 종료 오퍼레이터를 실행한 뒤 디스플레이 모드를 복원하고 네이티브 윈도우 리소스를 해제합니다.

---

## 알려진 문제 및 팁 (Known Issues & Tips)

- **듀얼 스레드 모드 Deprecated**: `ThreadMode.DUAL`은 `@Deprecated` 처리되었습니다. 최신 JVM 및 AWT 그래픽 환경에서는 `ThreadMode.SINGLE`과 `WaitMode.HYBRID` 또는 `WaitMode.OS_SLEEP`의 조합이 스레드 간 화면 찢김 없이 훨씬 안정적인 프레임 페이싱을 제공합니다.
- **macOS 창 크기 변경 이슈**: macOS 환경에서 기본 렌더링 옵션(`DEFAULT`)을 사용할 때 창 크기를 빠르게 조절하면 저프레임 구간에서 내부 그래픽 충돌이 발생할 수 있습니다. macOS 타깃 빌드 시 `RenderingOption.LEGACY` 사용을 권장합니다.
- **안전 런타임(Safe Runtime) 폴백**: `Config.Builder.setSafeRuntime(true)`를 활성화하면 로딩 실패 시 크래시가 발생하는 대신 경고 텍스처(`WrongTexture.png`)와 경고음으로 대체되고 풀이 부족할 경우 자동 확장되며 콘솔에 상세 원인이 로깅됩니다.
- **미문서화 내부 구현 및 소스코드 참조**: 엔진의 일부 내부 보조 루틴, 저수준 훅, 고급 유틸리티는 별도 문서가 충분하지 않을 수 있습니다. 릴리즈 에셋에 엔진의 전체 소스코드가 담긴 통합 PDF 파일이 동봉되어 있으므로, 세부 구현 원리나 미문서화 API의 동작 방식을 파악할 때는 해당 전체 소스코드 PDF를 LLM에 직접 입력하여 레퍼런스로 활용하시면 가장 정확하고 구체적인 해결책을 얻으실 수 있습니다.

---
---

# Olen Engine (PRE 0.4.0) - Suomi (Finnish)

Kevyt Java/AWT-pohjainen 2D-pelimoottori. Moottori tarjoaa suorituskykyisen muistinhallinnan, dynaamisen tekstuurikartastojen (texture atlas) pakkauksen, valmiin korealaisten merkkien (hangul) syöttömoduulin, staattisen analyysin ja IDE-tuen resurssi- ja skeniavaimille, puupohjaista automaattitäydennystä tukevan kehittäjäkonsolin, integroidun suorituskykyprofiloinnin ja telemetriajärjestelmän sekä luotettavan skenaarioiden elinkaaren sulavilla siirtymillä.

---

# MUUTOSLOKI (CHANGE LOG)

## PRE 0.4.0
- **Skenaarioarkkitehtuuri ja siirtymäputki**
    * `Scene`-luokan uudelleensuunnittelu laajennettavaksi abstraktiksi luokaksi täysillä elinkaarikoukuilla: `init(SceneInit)`, `update(dt)`, `render(Graphics2D)`, `onEnter()`, `onExit()`, ja `onDispose()`
    * Asynkroninen siirtymäputki säädettävällä häivytysnopeudella (`setFadeSpeed`) ja tiloilla (`TransitionPhase`: `TRANSITION_FADE_OUT`, `TRANSITION_LOADING`, `TRANSITION_FADE_IN`)
    * Mukautettava latausnäytön renderöintikoukku `LoadingRenderer`-rajapinnan kautta
    * Skenaariokohtainen tallennusrajapinta: `saveable`, `save(Properties)`, ja `load(Properties)`, joka tallentaa tiedot hakemistoon `~/.{projectName}/save/{sceneName}.properties`
    * Integroitu skenaariorekisteri (`registerScene`, `changeScene`) sekä skenaariokohtainen äänten elinkaari (`Sfx`, `Bgm`)
- **IDE-liitännäisen (`OlenEngineSupport`) skeniavainten tuki**
    * Staattinen indeksointi ja koodituki laajennettu skeniavaimiin (`SCENE`): automaattitäydennys, määrittelyyn siirtyminen, virhetarkistus ja refaktorointi kutsuille `changeScene("")` ja `setInitScene("")`
- **Resurssiputki ja rinnakkaisuus**
    * Yhtenäistetty `LoadMode` (`SYNC`, `LAZY`) metodien `loadTexture`, `loadSound`, ja `loadMusic` välillä
    * `DynamicAssetObject`-luokka: säie-turvallinen peruutus, salpatuki (`awaitCompletion`, `awaitCompletionOrThrow`) ja muistialtaiden kierrätys
    * Käynnistysresurssien rekisteröinti `AssetInit`-kautta välityspalvelinoliolla (`BootTextureProxy`, `BootSoundProxy`, `BootMusicProxy`) ja tekstuurikartastojen esileivonta (`registerBootAtlas`)
    * Suora bittikarttojen rekisteröinti: `registerDirectTexture(assetKey, BufferedImage)`
    * Resurssien vapautus (`free`) laajennettu kattamaan `ATLAS`-tyyppi `TEXTURE`-, `SOUND`- ja `MUSIC`-resurssien lisäksi
- **Grafiikkaputki ja näyttönhallinta**
    * Monitilainen renderöinti: Suora piirto (`DrawMode.DIRECT`) tai skaalattu välipuskuri (`DrawMode.BUFFER`)
    * Laajennettu `RenderingOption`: `LEGACY` (vakaa macOS-ikkunoiden koonmuutoksissa) ja modernisoitu `CRT`
    * Paranneltu CRT-emulaatio: 64-kaistainen kaksoisajo-tynnyrivääristymä (`CURVE_X`, `CURVE_Y`), CRT-värinä (`crtJitterEnabled`), juovitus (scanlines), loisteainemaski, pehmeä vinjetointi ja dynaaminen pulssivälkyntä
    * Näyttönhallinta: Yksinoikeudellinen kokoruututila (FSEM), dynaaminen resoluution ja virkistystaajuuden vaihto (`setDisplayResolution`) sekä laitteistokursorin koon säätö (`useEngineCursor`, `setCursorSize`)
    * Kokonaislukuskaalauksen kohdistuslaskenta korkean DPI-tarkkuuden laitemuunnoskorjauksella (`ViewMetrics`, `sanitizeDeviceScale`)
    * Hiiren koordinaattien oikaisu CRT-tynnyrivääristymässä (`updateVirtualMouseCrt`)
- **Kehittäjäkonsoli ja komentojärjestelmä**
    * Modulaarinen puupohjainen automaattitäydennysmoottori (`AutoCompleteManager`, `TreeBuilder`, `CandidateRule`, `Condition`)
    * Kaksiosainen ehdotusliittymä: ehdokasjoukko vasemmalla, järjestetyt vastaavuudet oikealla nuolinäppäinohjauksella
    * Reaaliaikaiset syntaksi- ja välilyöntivaroitukset (sulkemattomat lainausmerkit, peräkkäiset välilyönnit)
    * Sisäänrakennettu `sys`-järjestelmäkomentoalue:
        - `sys scene <sceneKey>`: Reaaliaikainen skenaarion vaihto lennosta
        - `sys debug asset <texture|sound|music|atlas> <key> [desktop|projectFolder|exeFolder]`: Resurssien tarkastelu ja PNG-vienti
        - `sys quickput <put|delete|clear>`: Makrojärjestelmä automaattisella täydennyksen päivityksellä ja pysyvällä tallennuksella
        - `sys exe <performanceReader|settingEditor|testInConsole>`: Sisäisten diagnostiikkatyökalujen käynnistys
        - `sys mem`, `sys thread`, `sys dump`, `sys getInfo`: JVM-muistin, säielistan ja järjestelmätietojen tarkastelu
- **Diagnostiikkatyökalut ja suorituskykyprofilointi**
    * Erillinen visualisointityökalu: `PerformanceReader` monivälilehtisillä kuvaajilla (`GraphTab`), panoroinnilla, zoomauksella, dynaamisella asteikonsäädöllä ja vaihekohtaisilla tilastoilla
    * Kattava telemetriankeruu (`PerformanceRecorder`): CPU-kuormitus, JVM-keko/ei-keko, GC-taukoajat, kehysaika, logiikan/renderöinnin työajat, käyttöjärjestelmä- ja laitteistotiedot
    * Asetusten hallintatyökalu: `EngineSettingEditor` konfiguraatioiden reaaliaikaiseen muokkaamiseen, uudelleenlataamiseen ja nollaamiseen
- **Vikasietoisuus ja vakaus**
    * Säiekohtainen poikkeusten vaimennus (`ErrorTracker`): Toistuvien peräkkäisten poikkeusten suodatus konsolin ja lokien tukkeutumisen estämiseksi
    * Visuaalinen ruutuhuomautusjärjestelmä (`ErrorBoxManager`) ajonaikaisten virheiden ja latausvirheiden välittömään näyttämiseen

## PRE 0.2.0
- Resurssiavaimien staattinen indeksointi ja IntelliJ IDEA -liitännäinen (`OlenEngineSupport`)
    * Automaattitäydennys, määrittelyyn siirtyminen, reaaliaikainen virhetarkistus ja nimeämisen refaktorointi avaimille (`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`)
- Standardoitu resurssienhallintarajapinta
    * Asynkronisen latauksen tapahtuma-avainten (`EVENT`) tuki
    * Resurssien vapautusrajapinta: `free(TYPE, key)`
    * Globaalin kontekstin avainindeksointi: `com.fw.main.Fw`
    * `Safe Runtime` -tila: kaatumisten esto korvaavalla tekstuurilla (`wrongTexture`) ja resurssialtaiden automaattisella kasvatuksella
- Vakauden ja elinkaaren parannukset
    * Kilpatilanteiden poisto siirtymissä ja moottorin sammutuksessa
- Renderöintiputki ja näyttöasetukset
    * CRT-varjostin ja -simulaatio (`RenderingOption.CRT`)
    * Yksinoikeudellinen kokoruututila (FSEM) ja dynaaminen näyttötilan vaihto
    * Virtuaaliresoluution määritys (`virtualScreenWidth`, `virtualScreenHeight`)
    * Mukautettu laitteistokursori (`EngineCursor.png`)
- Ajoituksen hallinta
    * Kehysten synkronoinnin odotustilat (`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`)
- Syötteiden ja konsolin parannukset
    * `KeyBindingBase`-optimointi: 65 536 avaimen staattinen taulukkoindeksointi
    * Responsiivinen konsoli ja automaattitäydennys
    * Kiinteä HUD-virheenkorjausnäyttö

## PRE 0.1.0
- Alustusarkkitehtuurin uudistaminen selkeään `init`-metodiin
- Suorituskykyprofilointi: `PerformanceRecorder` ja `PerformanceReader`
- Äänimoottorin palautus `TinySound`-järjestelmään

---

## IntelliJ IDEA -liitännäisopas

Monet moottorin resursseista ja skenaarioista tunnistetaan merkkijonoavaimilla. Erillisen liitännäisen asentaminen on erittäin suositeltavaa kirjoitusvirheiden välttämiseksi ja koodin sujuvan navigoinnin varmistamiseksi.

- Git-varasto: https://github.com/yooncrow33/OlenEnginePlugin

### Liitännäisen ominaisuudet
1. **Automaattitäydennys (Auto-completion)**: Tarjoaa rekisteröityjen avaimien ponnahdusvalikon metodeissa kuten `getTexture("")`, `changeScene("")` tai `setInitScene("")`
2. **Määrittelyyn siirtyminen (Ctrl + Click)**: Siirry suoraan avainmerkkijonosta sen rekisteröintikohtaan (esim. `registerBootTexture`, `registerScene`)
3. **Syntaksin ja virheiden tarkistus**: Korostaa rekisteröimättömät tai virheelliset avaimet reaaliajassa punaisella alleviivauksella
4. **Uudelleennimeämisen refaktorointi (Shift + F6)**: Päivittää avaimen nimen samanaikaisesti sen määrittelystä ja kaikista käyttökohteista koko projektissa

### Huomautus
- Testattu ympäristössä: #IC-252.26199.169.

---

## Keskeiset ominaisuudet (Key Features)

- **Edistyksellinen grafiikka- ja renderöintiputki**
    * Laitteistokiihdytetty kaksoispuskurointi `VolatileImage`- ja `BufferStrategy`-tekniikoilla
    * Monitilainen piirto: Suora renderöinti (`DrawMode.DIRECT`) tai puskuroitu skaalaus (`DrawMode.BUFFER`)
    * Dynaaminen tekstuurikartastojen luonti MaxRects-algoritmilla ja läpinäkyvien reunojen automaattisella leikkauksella (alpha-trimming)
    * Kokonaislukuihin kohdistuva ja murtolukumuotoinen fyysinen skaalaus korkean DPI-tarkkuuden tuella (`ViewMetrics`)
    * Perinteinen renderöintitila (`LEGACY`) macOS-yhteensopivuudelle sekä CRT-tynnyrivääristymäscanline- ja heijastusefekteillä

- **Skenaarioiden hallinta ja siirtymäarkkitehtuuri**
    * Ensiluokkainen `Scene`-elinkaaren hallinta puhtaalla tilojen erottelulla
    * Sisäänrakennettu asynkroninen siirtymäputki säädettävällä häivytysnopeudella ja vapaasti mukautettavalla latausnäytöllä (`LoadingRenderer`)
    * Skenaariokohtainen tietojen sarjallistaminen käyttäjähakemistoon (`saveable`, `save`, `load`)

- **Korkean suorituskyvyn äänimoottori (TinySound 1.1.1)**
    * Matalaviiveinen 44.1 kHz 16-bittinen Signed PCM -äänimikseri taustapäivityksellä
    * Toisto suoraan muistista (`MemSound`, `MemMusic`) tai tiedostovirrasta (`StreamMusic`, `StreamSound`)
    * Äänenvoimakkuuden, stereopanotoinnin (`[-1.0, 1.0]`), toistopisteiden ja yleisen äänenvoimakkuuden tarkka hallinta

- **Kehitysympäristö ja työkalut**
    * Pelinsisäinen kehittäjäkonsoli (`~`-näppäin) puurakenteisella automaattitäydennyksellä ja syntaksin tarkistuksella
    * Koreankielisen tekstinsyötön tuki (`TextModule`, `TextObject`) leikepöytäintegraatiolla ja visuaalisella kursorilla
    * Sisäänrakennettu diagnostiikkajärjestelmä: telemetriatiedostojen (`.fwD`) keruu (`PerformanceRecorder`) ja interaktiivinen visualisointi (`PerformanceReader`)
    * Asetustenhallintatyökalu (`EngineSettingEditor`) erillisten ominaisuustiedostojen tallennuksella hakemistoon `~/.{projectName}/EngineSettings/`
    * Virheiden toiston vaimennus ja visuaaliset ruutuilmoitukset (`ErrorBoxManager`)

---

## Järjestelmävaatimukset (Requirements)

- **JDK:** OpenJDK 21+ (GraalVM 21+ suositeltu)
- **Käyttöjärjestelmä:** Windows / macOS / Linux

---

## Aloitusopas (Getting Started)

```java
import com.fw.main.*;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.AssetManager;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
import com.fw.main.utils.platform.system.scene.Scene;

import java.awt.*;

public class MyGame extends Base {

    public MyGame() {
        super(new Base.Builder()
                // Alusta Core-konfiguraatio suoraan Base.Builder-rakentajassa
                .setCoreConfig(new Config.Builder("MyGameFolder")
                        .setWindowWidth(1280) // Alkuperäinen ikkunan leveys
                        .setWindowHeight(720) // Alkuperäinen ikkunan korkeus
                        .setUseKoreanModule(true) // Korean syöttömoduulin aktivointi
                        .setUseIntegerPhysicalScaling(true) // Fyysisen skaalauksen kokonaislukukohdistus
                        .setUseEncryption(false) // Tallennustiedostojen AES-salaus
                        .setSafeRuntime(true) // Käytä korvaavia resursseja kaatumisen sijaan
                )
                // Ikkuna- ja näyttöasetukset
                .setTitle("My Olen Game")
                .setFullScreen(false)
                .setDisplayResolution(1920, 1080, DisplayMode.BIT_DEPTH_MULTI, DisplayMode.REFRESH_RATE_UNKNOWN)
                .setVirtualScreenWidth(1920) // Loogisen piirtoalueen leveys
                .setVirtualScreenHeight(1080) // Loogisen piirtoalueen korkeus
                .setUseEngineCursor(true) // Käytä moottorin laitteistokursoria
                .setCursorSize(32, 32)
                
                // Renderöinti- ja suoritusvaihtoehdot
                .setRenderingOption(RenderingOption.DEFAULT) // DEFAULT, LEGACY tai CRT
                .setDrawMode(Base.DrawMode.DIRECT) // DIRECT tai BUFFER
                .setBufferSize(1920, 1080) // Puskurin koko BUFFER-tilassa
                .setWaitMode(Base.WaitMode.HYBRID) // OS_SLEEP, HYBRID tai BUSY_WAIT
                .setThreadMode(Base.ThreadMode.SINGLE) // SINGLE (suositeltu) tai DUAL
                .setCrtJitterEnabled(true) // CRT-juovituksen värinä
                
                // Siirtymät ja ajoitus
                .setFadeSpeed(3.0f) // Skenaarion häivytysnopeus
                .setLoadingRenderer((g, progress, message, w, h) -> {
                    // Mukautettu latausnäyttö
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                    g.setColor(Color.WHITE);
                    g.drawString(message + " (" + (int)(progress * 100) + "%)", w / 2 - 100, h / 2);
                })
                .setResizeSettleNanos(150_000_000L) // Odotusaika ikkunan koon muuttuessa
                .setShutdownTimeoutMs(2000) // Säikeiden sulkemisen aikakatkaisu
                .setBufferStrategyCount(2) // Kaksoispuskurointi
                .setRenderRetryCount(3) // Uudelleenyritykset pinnan nollauksessa
                .setCloseWindowWithKillVM(true) // Sulje prosessi ikkunan sulkeutuessa
                
                // Diagnostiikka ja kontekstit
                .setUseConsole(true) // Kehittäjäkonsoli (~ -näppäin)
                .setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01")
                .setStringKey("MAIN_GAME_INSTANCE")
                .setIntegerKey(1)
        );
    }

    @Override
    public void init(BaseInit init) {
        // Varaa resurssialtaiden koot
        assetManager.mallocTexturePool(500);
        assetManager.mallocLazyLoadPool(100);

        // Rekisteröi skenaariot (IDE-liitännäinen tarkistaa avaimet staattisesti)
        registerScene("MAIN_MENU", new MainMenuScene());
        registerScene("GAMEPLAY", new GameplayScene());

        // Aseta aloitusskenaario rekisteröidyllä avaimella
        init.setInitScene("MAIN_MENU");
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

### Skenaarion luominen (Scene)

```java
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.scene.Atlas;
import com.fw.main.utils.platform.system.scene.Bgm;
import com.fw.main.utils.platform.system.scene.Scene;
import com.fw.main.utils.platform.system.scene.Sprite;

import java.awt.*;
import java.util.Properties;

public class MainMenuScene extends Scene {

    private Sprite titleLogo;
    private Bgm menuMusic;

    @Override
    public void init(SceneInit init) throws Exception {
        // Luo skenaariokohtainen tekstuurikartasto
        init.createAtlas("MENU_ATLAS", 2, binder -> {
            titleLogo = binder.registerSprite(IoUtils.getGameResourceStream("logo.png"), "spr_logo");
        });

        // Rekisteröi skenaarioon sidottu taustamusiikki (suljetaan automaattisesti siirtymässä)
        menuMusic = init.registerMusic(IoUtils.getGameResourceStream("menu_theme.ogg"), true);

        // Ota automaattinen tilantallennus käyttöön
        this.saveable = true;
    }

    @Override
    protected void onEnter() {
        if (menuMusic != null) {
            menuMusic.getMusic().play(true, 0.8);
        }
    }

    @Override
    public void update(double dt) {
        // Skenaarion logiikkapäivitys
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 1920, 1080);

        if (titleLogo != null) {
            titleLogo.draw(g, 760, 200, 400, 200);
        }
    }

    @Override
    public void mClicked(FwMouseAPI e) {
        if (e.isLeftButton()) {
            // Skenaarion vaihto (liitännäinen tukee avaimen validointia ja refaktorointia)
            base.changeScene("GAMEPLAY");
        }
    }

    @Override
    protected void save(Properties props) {
        props.setProperty("high_score", "15400");
    }

    @Override
    protected void load(Properties props) {
        String highScore = props.getProperty("high_score", "0");
    }
}
```

---

## Resurssi- ja skeniavainjärjestelmä

Moottori ja `OlenEngineSupport`-liitännäinen tunnistavat avainten tyypit metodien nimien ja parametrien sijaintien (indeksien) perusteella.

### 1. Skenaarioavaimet (SCENE)
Siirtymissä ja käynnistyksessä käytettävät tunnisteet analysoidaan ja tarkistetaan staattisesti.

```java
// Rekisteröinti (1. argumentti: Skenaarion avain)
registerScene("SCENE_TITLE", new TitleScene());

// Aloitusskenaarion asetus
baseInit.setInitScene("SCENE_TITLE");

// Siirtymä ajon aikana
base.changeScene("SCENE_TITLE");
```

### 2. Tekstuurit (TEXTURE)
Tukee käynnistyksen välityspalvelimia, synkronista ja asynkronista latausta, suoraa bittikarttojen syöttöä ja instanssien hakua.

```java
// Käynnistysrekisteröinti (palauttaa BootTextureProxy-kahvan)
Texture bootTex = assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// Lataus ajon aikana (SYNC lataa heti, LAZY lataa taustasäikeessä)
assetManager.loadTexture(AssetManager.LoadMode.SYNC, "tex_stage_bg", IoUtils.getGameResourceStream("bg.png"), null);
assetManager.loadTexture(AssetManager.LoadMode.LAZY, "tex_heavy_bg", IoUtils.getGameResourceStream("heavy.png"), "EVENT_BG_READY");

// Suora bittikartan rekisteröinti
assetManager.registerDirectTexture("tex_procedural", generatedBufferedImage);

// Instanssin haku
Texture tex = assetManager.getTexture("tex_player");
VolatileImage vImg = tex.getVolatileImage();
```

### 3. Ääniefektit (SOUND)
Käsittelee suoraan muistiin ladattuja äänipuskureita.

```java
// Käynnistysrekisteröinti
SoundAsset bootSnd = assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// Lataus ajon aikana
assetManager.loadSound(AssetManager.LoadMode.SYNC, "snd_jump", IoUtils.getGameResourceStream("jump.wav"), null);

// Toisto
Sound snd = assetManager.getSound("snd_hit");
snd.play(1.0, 0.0); // voimakkuus: 1.0, pan: keskellä (0.0)
```

### 4. Taustamusiikki (MUSIC)
Hallitsee muistissa toistettavia tai suoraan levyltä suoratoistettavia musiikkikappaleita.

```java
// Käynnistysrekisteröinti
MusicAsset bootMusic = assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// Suoratoistettava lataus ajon aikana
assetManager.loadMusic(AssetManager.LoadMode.SYNC, AssetManager.MusicType.STREAM_MUSIC, "bgm_stage1", IoUtils.getGameResourceStream("stage1.ogg"), null);

// Toisto
Music music = assetManager.getMusic("bgm_main");
music.play(true, 0.75, 0.0); // toisto: true, voimakkuus: 0.75, pan: keskellä
```

### 5. Tekstuurikartastot ja spritet (SPRITE & ATLAS)
Pakkaa erilliskuvat kartastoiksi ja tarjoaa UV-koordinaatit.

```java
// Kartaston rekisteröinti käynnistyksen yhteydessä
assetInit.registerBootAtlas("GLOBAL_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("player_idle.png"), "spr_player_idle");
});

// Skenaariokohtainen kartaston luonti
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// UV-koordinaattien kysely
Atlas atlas = assetManager.getAtlas("GLOBAL_ATLAS");
float[] uv = atlas.getUV("spr_player_idle"); // [u1, v1, u2, v2]

// Spriten piirtäminen leikkauskorjauksilla
Sprite sprite = atlas.getSprite("spr_player_idle");
sprite.draw(g2d, 100, 100, 64, 64);
```

### 6. Globaali viitekehyskonteksti (FW)
Staattinen keskusrekisteri luokassa `com.fw.main.Fw`.

```java
import com.fw.main.Fw;

// Rekisteröinti
Fw.add("MAIN_ENGINE", this);
Fw.add(1, this);

// Haku
Base engine = Fw.get("MAIN_ENGINE");
```

### 7. Asynkroniset tapahtumat (EVENT)
Tapahtuma-avaimet asynkronisen latauksen vaiheiden seurantaan ja tehtävien käynnistämiseen.

```java
// Sido laiska tapahtuma-avain
sceneInit.bindLazyEvent("EVENT_CHAPTER_1_ASSETS");

// Käynnistä sidotut lataustehtävät
assetManager.event("EVENT_CHAPTER_1_ASSETS");

// Käynnistä ja odota valmistumista synkronisesti
assetManager.eventAndAwait("EVENT_CHAPTER_1_ASSETS");
```

### 8. Resurssien vapauttaminen (Free)
Poistaa resurssit aktiivisista taulukoista ja palauttaa puskurit muistialtaisiin.

```java
assetManager.free(AssetManager.AssetType.TEXTURE, "tex_stage_bg");
assetManager.free(AssetManager.AssetType.SOUND, "snd_jump");
assetManager.free(AssetManager.AssetType.MUSIC, "bgm_stage1");
assetManager.free(AssetManager.AssetType.ATLAS, "GAME_ATLAS");
```

---

## Vältettävät metodien määrittelyt

Nopean syntaksianalyysin takaamiseksi IDE-liitännäinen tunnistaa avaimet pelkästään **metodien nimien ja parametrien sijaintien** perusteella ilman tyyppipäättelyä.

Vältä luomasta omissa luokissasi metodeja, joilla on sama nimi ja parametrijärjestys kuin alla olevissa määrittelyissä, jotta tavallisia merkkijonoja ei tulkita vahingossa moottorin avaimiksi:

### Ristiriidoille alttiit metodit

1. **Resurssien määrittelymetodit**
    * `registerBootTexture(String key, ...)`: 1. parametri (indeksi 0) -> TEXTURE-avain
    * `registerBootSound(String key, ...)`: 1. parametri (indeksi 0) -> SOUND-avain
    * `registerBootMusic(String key, ...)`: 1. parametri (indeksi 0) -> MUSIC-avain
    * `registerDirectTexture(String key, ...)`: 1. parametri (indeksi 0) -> TEXTURE-avain
    * `registerSprite(..., String key)`: 2. parametri (indeksi 1) -> SPRITE-avain
    * `registerScene(String key, ...)`: 1. parametri (indeksi 0) -> SCENE-avain
    * `loadTexture(..., String key, ..., String eventKey)`: 2. parametri (indeksi 1) -> TEXTURE, 4. parametri (indeksi 3) -> EVENT-avain
    * `loadSound(..., String key, ..., String eventKey)`: 2. parametri (indeksi 1) -> SOUND, 4. parametri (indeksi 3) -> EVENT-avain
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3. parametri (indeksi 2) -> MUSIC, 5. parametri (indeksi 4) -> EVENT-avain

2. **Resurssien haku- ja elinkaarimetodit**
    * `getTexture(String key)`: 1. parametri (indeksi 0) -> TEXTURE-avain
    * `getSound(String key)`: 1. parametri (indeksi 0) -> SOUND-avain
    * `getMusic(String key)`: 1. parametri (indeksi 0) -> MUSIC-avain
    * `getAtlas(String key)`: 1. parametri (indeksi 0) -> ATLAS-avain
    * `getUV(String key)`: 1. parametri (indeksi 0) -> SPRITE-avain
    * `getSprite(String key)`: 1. parametri (indeksi 0) -> SPRITE-avain
    * `changeScene(String key)`: 1. parametri (indeksi 0) -> SCENE-avain
    * `setInitScene(String key)`: 1. parametri (indeksi 0) -> SCENE-avain
    * `event(String key)`: 1. parametri (indeksi 0) -> EVENT-avain
    * `eventAndAwait(String key)`: 1. parametri (indeksi 0) -> EVENT-avain
    * `bindLazyEvent(String key)`: 1. parametri (indeksi 0) -> EVENT-avain
    * `free(AssetType type, String key)` / `free(String type, String key)`: 2. parametri (indeksi 1) -> vastaava resurssiavain tyypin mukaan

---

## Kehittäjän työkalut ja diagnostiikkajärjestelmät

### 1. Pelinsisäinen kehittäjäkonsoli (`~`-näppäin)
Quake-tyylinen kehittäjäkonsoli reaaliaikaisella syntaksin validoinnilla ja puupohjaisella automaattitäydennyksellä:
- **Ohjaus**: `Page Up` / `Page Down` selaa lokia, `Ylös` / `Alas` selaa ehdotuksia tai komentohistoriaa, `Tab` suorittaa täydennyksen.
- **Dynaaminen täydennys**: Synkronoituu rekisteröityjen skenaarioiden, aktiivisten tekstuurien/äänten ja pikakomentojen kanssa.
- **Keskeiset sisäänrakennetut komennot**:
    * `sys scene <sceneKey>`: Vaihda skenaariota ajonaikaisesti.
    * `sys debug asset texture <key> <desktop|projectFolder|exeFolder>`: Tallenna tekstuuri PNG-kuvana levylle.
    * `sys debug asset atlas <key> <desktop|projectFolder|exeFolder>`: Tallenna kokonainen kartastokuva PNG-tiedostona.
    * `sys debug asset sound <key>` / `sys debug asset music <key> <true|false>`: Testaa ja ohjaa äänentoistoa.
    * `sys quickput put <key> "<command>"`: Määritä omia komentomakroja.
    * `sys exe performanceReader`: Avaa suorituskyvyn visualisointityökalu.
    * `sys exe settingEditor`: Avaa moottorin asetuseditori.
    * `sys mem` / `sys thread`: Näytä JVM-muistin tila ja aktiiviset säikeet.
    * `sys dump`: Vie konsolin lokitiedot polkuun `~/.{projectName}/logs/console_{timestamp}.txt`.

### 2. Integroitu suorituskykytelemetria (`PerformanceRecorder` & `PerformanceReader`)
Määritä tietojen keruu komennolla `Base.Builder.setPerformanceRecorderOption()`:
```java
builder.setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "bench_run_01");
```
- Tallentaa CPU-käytön, kekomuistin, GC-tauot, kehysajat, CPU/GPU-työajat ja laitteistotiedot.
- Kerätyt `.fwD`-profiilitiedostot voidaan avata interaktiiviseen tarkasteluun `PerformanceReader`-ohjelmassa:
    * **Hiiren vasen painike + veto**: Panoroi aikajanalla.
    * **Hiiren oikea painike + veto pystysuunnassa**: Säädä kuvaajan ylärajaa (Cap).
    * **Hiiren rulla**: Zoomaa aikajanaa.
    * Klikkaa vaihepainikkeita alareunasta tarkastellaksesi jakson keskiarvoja ja ääriarvoja.

### 3. Asetustenhallinta (`EngineSettingEditor`)
Asetukset tallennetaan yksittäisinä ominaisuustiedostoina hakemistoon `~/.{projectName}/EngineSettings/`.
- Käynnistä kutsumalla `new EngineSettingEditor().setVisible(true);` tai konsolikomennolla `sys exe settingEditor`.
- Mahdollistaa näyttötilan, resoluution, säietilan, odotustilan ja puskurikokojen reaaliaikaisen muokkaamisen sekä asetusten täyden nollauksen.

---

## Suositellut JVM-asetukset (Recommended VM Options)

- Pakota DirectX-kiihdytys (Windows D3D)
```
-Dsun.java2d.d3d=true
```

- Pakota OpenGL-kiihdytys (Linux / macOS)
```
-Dsun.java2d.opengl=true
```

- Pakota OpenGL Framebuffer Objects (FBO)
```
-Dsun.java2d.opengl.fbobject=true
```

- Poista vanha DirectDraw käytöstä
```
-Dsun.java2d.noddraw=true
```

- Nollaa VRAM-kiihdytyksen kynnysarvo
```
-Dsun.java2d.accthreshold=0
```

- Ota ZGC käyttöön (Erittäin pienen viiveen roskienkeruu)
```
-XX:+UseZGC
```

- Ota sukupolvipohjainen ZGC käyttöön (JDK 21+)
```
-XX:+ZGenerational
```

---

## Moottorin elinkaari (Life Cycle)

1. **Käynnistysvaihe (Pääsäie)**:
    * Lukee ja vahvistaa `Config`-määritykset ja tallennetut asetukset (`EngineSettings`).
    * Luo ikkunakehyksen (`Base`), alustaa syöterajapinnat ja luo puskurointistrategian.
2. **Asynkroninen esilataus (`Async-Loader`-säie)**:
    * Suorittaa metodit `BaseInit` ja `onAssetInit`.
    * Varaa muistialtaat, sitoo käynnistysresurssit ja leipoo käynnistyskartastot.
    * Lataa pelitallenteet (`io.load`) ja valmistelee aloitusskenaarion putken.
3. **Pääsilmukka**:
    * **Yksisäie-tila (`SINGLE`, Suositeltu/Oletus)**: Pääsilmukka suorittaa tilapäivitykset (`stepEngineUpdate(dt)`) ja renderöinnin (`renderLoop()`) peräkkäin tarkalla ajoituksella (`WaitMode`).
    * **Kaksisäie-tila (`DUAL`, Vanhentunut/Deprecated)**: Jakaa logiikan (`logicThread`) ja renderöinnin (`renderThread`) erillisiin rinnakkaisiin säikeisiin.
4. **Skenaarion elinkaari ja siirtymät**:
    * Käynnistyy kutsusta `changeScene(key)`.
    * Nykyinen skenaario siirtyy tilaan `TRANSITION_FADE_OUT`, suorittaa metodit `onExit()` ja `dispose()`, ja vapauttaa sidotut äänet ja kartastot.
    * Uusi skenaario alustetaan taustasäikeessä (`init(sceneInit)`), resurssit leivotaan, metodi `onEnter()` suoritetaan, ja näkymä häivytetään esiin (`TRANSITION_FADE_IN`).
5. **Hallittu sammutus (Safe Shutdown)**:
    * Tunnistaa ikkunan sulkeutumisen ja aloittaa turvallisen alasajon.
    * Odottaa suoritussäikeiden sulkeutumista määritetyn aikarajan puitteissa (`shutdownTimeoutMs`).
    * Tallentaa pelin ja aktiivisen skenaarion tilat levylle (`save()`), suorittaa poistumisoperaattorit, palauttaa alkuperäisen näyttötilan ja vapauttaa laitteistoresurssit.

---

## Tunnetut ongelmat ja vinkit (Known Issues & Tips)

- **Kaksisäie-tilan vanhentuminen**: `ThreadMode.DUAL` on merkitty vanhentuneeksi (`@Deprecated`). Nykyaikaisissa JVM- ja AWT-ympäristöissä `ThreadMode.SINGLE` yhdistettynä tilaan `WaitMode.HYBRID` tai `WaitMode.OS_SLEEP` tarjoaa tasaisemman ruudunpäivityksen ilman säikeiden välistä kuvan repeilyä.
- **macOS-ikkunan koon muuttaminen**: Oletusrenderöinnillä (`DEFAULT`) ikkunan nopea koon muuttaminen macOS-järjestelmissä voi aiheuttaa AWT:n sisäisiä kaatumisia. macOS-alustalle kohdennetuissa julkaisuissa suositellaan käyttämään tilaa `RenderingOption.LEGACY`.
- **Vikasietoinen Safe Runtime -tila**: Kun asetus `Config.Builder.setSafeRuntime(true)` on käytössä, puuttuvat tai vioittuneet resurssit eivät kaada peliä. Moottori korvaa ne automaattisesti huomiotekstuurilla (`WrongTexture.png`) ja varoitusäänillä, kasvattaa resurssialtaita dynaamisesti ja kirjaa varoitukset kehittäjäkonsoliin.
- **Dokumentoimattomat toteutukset ja lähdekoodin hyödyntäminen**: Koska moottorin kaikkia sisäisiä apurutiineja, matalan tason koukkuja ja rajapintoja ei ole täysin katettu erillisessä dokumentaatiossa, moottorin koko lähdekoodi on paketoitu mukaan julkaisun liitetiedostoihin yhtenä PDF-tiedostona. Kun tutkit syvempiä arkkitehtuurisia yksityiskohtia tai ratkaiset monimutkaisia ajonaikaisia ongelmia, voit syöttää tämän lähdekoodi-PDF:n suoraan LLM:n kontekstiin tarkan ja kontekstitietoisen toteutusavun saamiseksi.
