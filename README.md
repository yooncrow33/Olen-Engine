# Olen Engine (0.2.0)

A lightweight 2D game engine based on Java/AWT. It features high-performance memory management, dynamic texture atlas packing, a completed Hangul (Korean) input module, and static analysis/IDE assistance for resource string keys.

---

# CHANGE LOG

## PRE 0.2.0
* Resource key static indexing and IntelliJ IDEA plugin (`OlenEngineSupport`) integration
    * Autocompletion, go-to-declaration, real-time error checking, and batch renaming for string resource keys (`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`)
* Standardized resource management API and asset pipeline
    * Asynchronous asset loading completion event key (`EVENT`) integration
    * Explicit resource release API: `free(TYPE, key)`
    * Dedicated key indexing for global framework context: `com.fw.main.Fw`
    * Introduced `Safe Runtime` mode: prevents crash exceptions on load failures with a fallback texture (`wrongTexture`), supports automatic asset pool expansion
* Enhanced stability and lifecycle
    * Eliminated potential race conditions during scene transitions and engine shutdown
* Rendering pipeline and display control
    * Added CRT shader and simulation (`RenderingOption.CRT`)
    * Support for Fullscreen Exclusive Mode (FSEM) and dynamic display mode switching (resolution, bit depth, refresh rate)
    * Custom virtual screen resolution configuration (`virtualScreenWidth`, `virtualScreenHeight`)
    * Engine-dedicated custom hardware cursor (`EngineCursor.png`) support
    * Removed experimental draw call caching system (`RenderingOption.EXPERIMENTAL`, `Call`, `LazyCall`, `StaticCall`)
* Frame timing
    * Introduced precise frame synchronization wait modes (`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`)
* Input and debugging/console system improvements
    * Optimized `KeyBindingBase` input processing: eliminated Swing `ActionMap` overhead, switched to lightweight 65,536 static key table indexing, fixed inverted binding options bug
    * Responsive console UI and autocompletion navigation improvements: UI scaling bound to screen resolution, arrow key (UP/DOWN) command suggestion navigation
    * Added persistent on-screen debug HUD

## PRE 0.1.0
* Initialization architecture improvements
    * Switched from default constructor-based initialization to explicit initialization (`init`)
    * Support for returning proxy objects during `AssetInit` bootstrapping
* Performance profiling
    * Added `PerformanceRecorder` and `PerformanceReader`
* Sound
    * Rolled audio engine back to `TinySound`

---

## IntelliJ IDEA Plugin Guide

Many resources within the engine (textures, sounds, sprites, etc.) are identified by string keys. Installing the dedicated plugin is highly recommended to prevent typos and ensure smooth code navigation.

* Repository: https://github.com/yooncrow33/OlenEnginePlugin

### Plugin Features
1. **Auto-completion**: Provides a registered key popup list inside resource lookup methods such as `getTexture("")`
2. **Go to Declaration (Ctrl + Click)**: Jump directly from a usage string literal to its registration declaration (e.g., `registerBootTexture`)
3. **Syntax / Error Inspection**: Real-time warning highlights (red underline) for unregistered or mistyped keys
4. **Rename Refactoring (Shift + F6)**: Simultaneously refactors the registration declaration and all usages when renaming a key

### Notice
- Tested exclusively on #IC-252.26199.169.

---

## Key Features

* **Advanced Graphics Pipeline**
    * Double-buffered rendering based on `VolatileImage` and `BufferStrategy`
    * Real-time alpha cropping and dynamic texture atlas generation using the MaxRects algorithm
    * Fractional and integer physical scaling calculations

* **High-Performance Sound Engine (TinySound)**
    * Low-latency audio processing built on an audio mixer
    * Streaming and memory-resident playback, real-time panning, and volume control

* **Developer Experience & Tooling**
    * In-game developer console (`~` key, tree-based autocompletion)
    * Completed Hangul composition and clipboard copy/paste support (`TextModule`) in AWT environments
    * AES-encrypted save/load operations and asynchronous asset manager
    * Static string key verification and refactoring via `OlenEnginePlugin`

---

## Requirements

* **JDK:** OpenJDK 21+ (GraalVM 21+ recommended)
* **OS:** Windows / macOS / Linux

---

## Getting Started

```java
public class MyGame extends Base {

    static {
        Core.setConfig(new Config.Builder("MyGameFolder") // Project folder created at ~/.MyGameFolder
                .setWindowWidth(1280) // Actual window width (virtual resolution can be set separately)
                .setWindowHeight(720) // Actual window height
                .setUseKoreanModule(true) // Enables the Korean input module
                .setUseIntegerPhysicalScaling(true) // Snaps physical scaling to integer values
                .setUseEncryption(false) // Applies AES encryption to save files
                .build());
    }

    public MyGame() {
        super(new Builder()
                .setUseConsole(true) // Enables in-game developer console
                .setRenderingOption(RenderingOption.DEFAULT) // Rendering option (DEFAULT / CRT)
                .setCloseWindowWithKillVM(true) // Terminates process completely upon window close
        );
    }

    @Override
    public void init(BaseInit init) {
        // Allocate and initialize memory pools
        assetManager.mallocTexturePool(1000);
        assetManager.mallocLazyLoadPool(200);

        // Register initial scene and build texture atlas
        init.setInitScene(new Scene.Builder(sceneInit -> {
            sceneInit.createAtlas("DEFAULTATLAS", 2, binder -> {
                binder.registerSprite(IoUtils.getGameResourceStream("sample.png"), "sample_key");
            });
        }).setName("MAIN_SCENE").build());
    }

    @Override
    public void update(double dt) {
        // Game logic update loop
    }

    @Override
    public void render(Graphics2D g) {
        // Rendering pipeline
        g.setColor(Color.WHITE);
        g.drawString("Olen Engine 0.2.0", 50, 50);
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

---

## Resource Key System & Code Examples

The plugin and engine identify key types by parsing designated method invocations and parameter argument positions.

### 1. Texture (TEXTURE)
Supports boot-time registration, runtime asynchronous loading, and instance retrieval.

```java
// Declaration 1: Boot registration (1st argument: Key)
assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// Declaration 2: Runtime async loading (2nd argument: Key, 4th argument: Completion event key)
assetManager.loadTexture("assets/bg.png", "tex_stage_bg", 0, "EVENT_BG_LOAD");

// Usage: Retrieve texture instance (1st argument: Target key)
Texture tex = assetManager.getTexture("tex_player");
```

### 2. Sound Effects (SOUND)
Handles registration and playback of short sound effect files.

```java
// Declaration 1: Boot registration (1st argument: Key)
assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// Declaration 2: Runtime async loading (2nd argument: Key, 4th argument: Completion event key)
assetManager.loadSound("assets/jump.wav", "snd_jump", 1, "EVENT_JUMP_LOAD");

// Usage: Retrieve sound instance (1st argument: Target key)
Sound snd = assetManager.getSound("snd_hit");
snd.play();
```

### 3. Music (MUSIC)
Manages streaming background music (BGM) resources.

```java
// Declaration 1: Boot registration (1st argument: Key)
assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// Declaration 2: Runtime async loading (3rd argument: Key, 5th argument: Completion event key)
assetManager.loadMusic("assets/stage1.ogg", StreamType.FILE, "bgm_stage1", 1.0f, "EVENT_STAGE1_LOAD");

// Usage: Retrieve music instance (1st argument: Target key)
Music music = assetManager.getMusic("bgm_main");
music.play(true);
```

### 4. Sprite Atlas (SPRITE)
Registers sub-images within the atlas builder and queries UV coordinates.

```java
// Declaration: registerSprite (2nd argument: Sprite key)
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// Usage: Query UV coordinates (1st argument: Target key)
UVRegion uv = assetManager.getUV("spr_btn_play");
```

### 5. Framework Base (FW)
Maps global state repository keys in the `com.fw.main.Fw` class.

```java
import com.fw.main.Fw;

// Declaration: setStringKey or Fw.add (1st argument: Key)
Fw.setStringKey("CONF_LOCALE", "ko_KR");
Fw.add("GLOBAL_VOLUME", 0.8f);

// Usage: Fw.get (1st argument: Key)
Object conf = Fw.get("CONF_LOCALE");
```

### 6. Events (EVENT)
Event keys to listen for after asynchronous loading completes or to trigger explicitly.

```java
// Declaration: Registered as event arguments in loadTexture, loadSound, loadMusic, etc.

// Usage: Dispatch event or branch handler (1st argument: Event key)
eventManager.event("EVENT_STAGE_CLEAR");

public void onEvent(String evt) {
    if ("EVENT_BG_LOAD".equals(evt)) {
        // Logic executed after loading completes
    }
}
```

### 7. Resource Disposal (Free)
Passes a type identifier and key to clean up specific resources from memory.

```java
// Usage: free (1st argument: Type name, 2nd argument: Resource key)
assetManager.free("TEXTURE", "tex_stage_bg");
assetManager.free("SOUND", "snd_jump");
assetManager.free("MUSIC", "bgm_stage1");
```

---

## Prohibited Conflicting Method Declarations

To ensure fast analysis during indexing phases (Dumb Mode), the IDE plugin does not strictly backtrack receiver class types on every call. Instead, it identifies resource keys based purely on **method names and parameter positions (indices)**.

Therefore, declaring methods in custom classes with identical names and string parameter positions as listed below may cause standard string literals to be misidentified as engine resource keys. This could result in false error underlines, unexpected autocompletion popups, or unintentional modifications during batch rename refactoring (Shift + F6).

### Conflict-Prone Method List

1. **Resource Definition Methods**
    * `registerBootTexture(String key, ...)`: 1st argument (index 0) recognized as TEXTURE key
    * `registerBootSound(String key, ...)`: 1st argument (index 0) recognized as SOUND key
    * `registerBootMusic(String key, ...)`: 1st argument (index 0) recognized as MUSIC key
    * `setStringKey(String key, ...)`: 1st argument (index 0) recognized as FW base key
    * `registerSprite(Object source, String key)`: 2nd argument (index 1) recognized as SPRITE key
    * `loadTexture(..., String key, ..., String eventKey)`: 2nd argument (index 1) recognized as TEXTURE, 4th argument (index 3) as EVENT key
    * `loadSound(..., String key, ..., String eventKey)`: 2nd argument (index 1) recognized as SOUND, 4th argument (index 3) as EVENT key
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3rd argument (index 2) recognized as MUSIC, 5th argument (index 4) as EVENT key

2. **Resource Usage Methods**
    * `getTexture(String key)`: 1st argument (index 0) recognized as TEXTURE key
    * `getSound(String key)`: 1st argument (index 0) recognized as SOUND key
    * `getMusic(String key)`: 1st argument (index 0) recognized as MUSIC key
    * `getUV(String key)`: 1st argument (index 0) recognized as SPRITE key
    * `event(String key)`: 1st argument (index 0) recognized as EVENT key
    * `free(String type, String key)`: If 1st argument contains "TEXTURE", "SOUND", or "MUSIC", the 2nd argument (index 1) is recognized as the corresponding resource key

> Note: `Fw.add()` and `Fw.get()` explicitly verify calls against `com.fw.main.Fw` and will not conflict with `add()` or `get()` methods in general user classes.

---

## Recommended VM Options

* Force DirectX
```
-Dsun.java2d.d3d=true
```

* Force OpenGL
```
-Dsun.java2d.opengl=true
```

* Force OpenGL FBO
```
-Dsun.java2d.opengl.fbobject=true
```

* Disable Legacy DirectDraw
```
-Dsun.java2d.noddraw=true
```

* Reset VRAM Cache Acceleration Threshold
```
-Dsun.java2d.accthreshold=0
```

* Enable ZGC (Sub-millisecond Latency)
```
-XX:+UseZGC
```

* Enable Generational ZGC
```
-XX:+ZGenerational
```

---

## Life Cycle

1. **Bootstrapping (Main Thread)**: Validates `Core` configuration, creates the window, and launches render/logic threads
2. **Async Loading (`Async-Loader Thread`)**: Configures system modules, binds boot assets, loads save data, and activates the initial scene
3. **Multi-Threaded Loop**: Runs logic thread (`update(dt)`) and render thread (`render()`) concurrently
4. **Scene Lifecycle**: When calling `changeScene()`, previous scene is disposed (`dispose()`) -> garbage queue collected -> new scene loaded asynchronously
5. **Safe Shutdown**: Synchronizes save data, joins worker threads, and releases window and graphics resources

---

## Known Issues & Tips

* <s>**Unstable Sound Engine**
    * Inability to use `.ogg` extension
    * Inability to use `Music` without streaming options
    * Audio distortion when mono-channel sounds are loaded into memory rather than streamed</s>

---

* **Unstable API**
    * Incomplete encapsulation
    * Some unstable API structures

---

# Olen Engine (0.2.0)

Java/AWT 기반 경량 2D 게임 엔진입니다. 고성능 메모리 관리, 동적 텍스처 아틀라스 패킹, 완성형 한글 입력 모듈 및 리소스 문자열 키 정적 분석/IDE 보조 시스템을 지원합니다.

---

# CHANGE LOG

## PRE 0.2.0
* 리소스 키 정적 인덱싱 및 IntelliJ IDEA 플러그인(`OlenEngineSupport`) 연동 지원
    * 문자열 리소스 키(`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`)의 자동완성, 선언부 추적, 실시간 에러 검사, 일괄 변경 지원
* 리소스 관리 API 규격화 및 에셋 파이프라인
    * 비동기 에셋 로딩 완료 이벤트 키(`EVENT`) 연동
    * `free(TYPE, key)` 명시적 리소스 해제 API 지원
    * 전역 프레임워크 컨텍스트 `com.fw.main.Fw` 전용 키 인덱싱 적용
    * `Safe Runtime` 모드 도입: 로딩 실패 시 예외 중단 방지 및 대체 텍스처(`wrongTexture`) 폴백, 에셋 풀 자동 확장 지원
* 안정성 및 수명주기 상향
    * 씬 전환, 셧다운 시의 레이스 컨디션 가능성 제거
* 렌더링 파이프라인 및 디스플레이 제어
    * CRT 셰이더 및 시뮬레이션(`RenderingOption.CRT`) 신설
    * 전체화면 단독 모드(FSEM) 및 디스플레이 모드(해상도, 비트 심도, 주사율) 동적 전환 지원
    * 가상 화면 해상도(`virtualScreenWidth`, `virtualScreenHeight`) 커스텀 설정 지원
    * 엔진 전용 커스텀 하드웨어 커서(`EngineCursor.png`) 설정 지원
    * 실험적 드로우콜 캐싱 시스템(`RenderingOption.EXPERIMENTAL`, `Call`, `LazyCall`, `StaticCall`) 제거
* 프레임 타이밍
    * 정밀 프레임 동기화 대기 모드(`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`) 도입
* 입력 및 디버깅/콘솔 시스템 개선
    * `KeyBindingBase` 입력 처리 최적화: Swing `ActionMap` 오버헤드를 제거하고 65,536 정적 키 테이블 인덱싱 조회로 경량화, 바인딩 옵션 반전 버그 수정
    * 반응형 콘솔 UI 및 자동완성 탐색 개선: 화면 해상도 연동 UI 스케일링, 방향키(UP/DOWN) 추천 명령어 선택 기능 지원
    * 화면 상시 디버그 HUD 추가

## PRE 0.1.0
* 초기화 구조 개선
    * 기본 생성자 기반 초기화에서 명시적 초기화(`init`)로 전환
    * `AssetInit` 부팅 시 프록시 객체 반환 지원
* 성능 프로파일링
    * `PerformanceRecorder` 및 `PerformanceReader` 추가
* 사운드
    * `TinySound`로 오디오 엔진 롤백

---

## IntelliJ IDEA Plugin Guide

엔진 내 많은 리소스(텍스처, 사운드, 스프라이트 등)는 스트링 키로 식별됩니다. 오타 방지 및 코드 네비게이션을 위해 전용 플러그인 설치를 권장합니다.

* Repository: https://github.com/yooncrow33/OlenEnginePlugin

### Plugin Features
1. **Auto-completion**: `getTexture("")` 등 리소스 조회 함수 내에서 등록된 키 목록 팝업 제공
2. **Go to Declaration (Ctrl + Click)**: 사용처 문자열에서 등록 선언부(`registerBootTexture` 등)로 점프
3. **Syntax / Error Inspection**: 등록되지 않은 오타 문자열 실시간 경고(빨간 밑줄) 표시
4. **Rename Refactoring (Shift + F6)**: 키 이름 변경 시 등록부와 모든 사용처 동시 리팩토링

### Notice
- Tested exclusively on #IC-252.26199.169.

---

## Key Features

* **Advanced Graphics Pipeline**
    * `VolatileImage` 및 `BufferStrategy` 기반 이중 버퍼링 렌더링
    * 실시간 알파 크롭(Alpha Cropping) 및 MaxRects 알고리즘 기반 동적 텍스처 아틀라스 생성
    * 정수/유수 물리 스케일링(Fractional / Integer Physical Scaling) 계산

* **High-Performance Sound Engine (TinySound)**
    * 오디오 믹서 기반 저지연 오디오 프로세싱
    * 스트리밍 및 메모리 상주 재생, 실시간 패닝(Pan) 및 볼륨 제어 지원

* **Developer Experience & Tooling**
    * 인게임 개발자 콘솔 (`~` 키, 트리 기반 자동완성)
    * AWT 환경 완성형 한글 조합 및 클립보드 복사/붙여넣기(`TextModule`) 지원
    * AES 암호화 데이터 저장/로드 및 비동기 에셋 관리자
    * `OlenEnginePlugin` 연동 문자열 키 정적 검증 및 리팩토링 지원

---

## Requirements

* **JDK:** OpenJDK 21+ (GraalVM 21+ 권장)
* **OS:** Windows / macOS / Linux

---

## Getting Started

```java
public class MyGame extends Base {

    static {
        Core.setConfig(new Config.Builder("MyGameFolder") // Project folder created at ~/.MyGameFolder
                .setWindowWidth(1280) // Actual window width (virtual resolution can be set separately)
                .setWindowHeight(720) // Actual window height
                .setUseKoreanModule(true) // Enables the Korean input module
                .setUseIntegerPhysicalScaling(true) // Snaps physical scaling to integer values
                .setUseEncryption(false) // Applies AES encryption to save files
                .build());
    }

    public MyGame() {
        super(new Builder()
                .setUseConsole(true) // Enables in-game developer console
                .setRenderingOption(RenderingOption.DEFAULT) // Rendering option (DEFAULT / CRT)
                .setCloseWindowWithKillVM(true) // Terminates process completely upon window close
        );
    }

    @Override
    public void init(BaseInit init) {
        // Allocate and initialize memory pools
        assetManager.mallocTexturePool(1000);
        assetManager.mallocLazyLoadPool(200);

        // Register initial scene and build texture atlas
        init.setInitScene(new Scene.Builder(sceneInit -> {
            sceneInit.createAtlas("DEFAULTATLAS", 2, binder -> {
                binder.registerSprite(IoUtils.getGameResourceStream("sample.png"), "sample_key");
            });
        }).setName("MAIN_SCENE").build());
    }

    @Override
    public void update(double dt) {
        // Game logic update loop
    }

    @Override
    public void render(Graphics2D g) {
        // Rendering pipeline
        g.setColor(Color.WHITE);
        g.drawString("Olen Engine 0.2.0", 50, 50);
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

---

## Resource Key System & Code Examples

플러그인과 엔진은 지정된 메서드 호출과 인자 위치를 파싱하여 키 종류를 식별합니다.

### 1. Texture (TEXTURE)
부팅 단계 등록 및 런타임 비동기 로딩, 인스턴스 조회를 지원합니다.

```java
// 선언 1: 부팅 등록 (1번째 인자: 키)
assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// 선언 2: 런타임 비동기 로드 (2번째 인자: 키, 4번째 인자: 완료 이벤트 키)
assetManager.loadTexture("assets/bg.png", "tex_stage_bg", 0, "EVENT_BG_LOAD");

// 사용처: 텍스처 인스턴스 획득 (1번째 인자: 대상 키)
Texture tex = assetManager.getTexture("tex_player");
```

### 2. Sound Effects (SOUND)
짧은 효과음 파일의 등록 및 재생을 처리합니다.

```java
// 선언 1: 부팅 등록 (1번째 인자: 키)
assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// 선언 2: 런타임 비동기 로드 (2번째 인자: 키, 4번째 인자: 완료 이벤트 키)
assetManager.loadSound("assets/jump.wav", "snd_jump", 1, "EVENT_JUMP_LOAD");

// 사용처: 사운드 획득 (1번째 인자: 대상 키)
Sound snd = assetManager.getSound("snd_hit");
snd.play();
```

### 3. Music (MUSIC)
스트리밍 방식의 BGM 리소스를 관리합니다.

```java
// 선언 1: 부팅 등록 (1번째 인자: 키)
assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// 선언 2: 런타임 비동기 로드 (3번째 인자: 키, 5번째 인자: 완료 이벤트 키)
assetManager.loadMusic("assets/stage1.ogg", StreamType.FILE, "bgm_stage1", 1.0f, "EVENT_STAGE1_LOAD");

// 사용처: 음악 획득 (1번째 인자: 대상 키)
Music music = assetManager.getMusic("bgm_main");
music.play(true);
```

### 4. Sprite Atlas (SPRITE)
아틀라스 빌더 내부에서 서브 이미지를 등록하고 UV 좌표를 가져옵니다.

```java
// 선언: registerSprite (2번째 인자: 스프라이트 키)
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// 사용처: UV 좌표 조회 (1번째 인자: 대상 키)
UVRegion uv = assetManager.getUV("spr_btn_play");
```

### 5. Framework Base (FW)
`com.fw.main.Fw` 클래스의 전역 상태 저장소 키를 매핑합니다.

```java
import com.fw.main.Fw;

// 선언: setStringKey 또는 Fw.add (1번째 인자: 키)
Fw.setStringKey("CONF_LOCALE", "ko_KR");
Fw.add("GLOBAL_VOLUME", 0.8f);

// 사용처: Fw.get (1번째 인자: 키)
Object conf = Fw.get("CONF_LOCALE");
```

### 6. Events (EVENT)
비동기 로딩 완료 후 수신하거나 명시적으로 트리거할 이벤트 키입니다.

```java
// 선언: loadTexture/loadSound/loadMusic 등의 이벤트 인자로 등록

// 사용처: 이벤트 발송 또는 핸들러 분기 (1번째 인자: 이벤트 키)
eventManager.event("EVENT_STAGE_CLEAR");

public void onEvent(String evt) {
    if ("EVENT_BG_LOAD".equals(evt)) {
        // 로딩 완료 후 처리
    }
}
```

### 7. Resource Disposal (Free)
타입 식별자와 키를 전달하여 특정 리소스를 메모리에서 정리합니다.

```java
// 사용처: free (1번째 인자: 타입 명칭, 2번째 인자: 리소스 키)
assetManager.free("TEXTURE", "tex_stage_bg");
assetManager.free("SOUND", "snd_jump");
assetManager.free("MUSIC", "bgm_stage1");
```

---

## Prohibited Conflicting Method Declarations

IDE 플러그인은 빠른 분석 및 인덱싱 단계(Dumb Mode) 지원을 위해 호출 대상 클래스의 타입을 매번 엄격하게 역추적하지 않고, **메서드 이름과 파라미터 위치(인덱스)**를 기준으로 리소스 키를 식별합니다.

따라서 사용자 정의 클래스에 아래와 동일한 이름 및 문자열 파라미터 순서를 가진 메서드를 선언할 경우, 일반 문자열이 엔진 리소스 키로 오인되어 잘못된 오류 밑줄 표시, 의도치 않은 자동완성 팝업, 일괄 이름 변경(Shift + F6) 시 동시 수정 등의 문제가 발생할 수 있습니다.

### Conflict-Prone Method List

1. **Resource Definition Methods**
    * `registerBootTexture(String key, ...)`: 1번째(index 0) 인자가 TEXTURE 키로 인식됨
    * `registerBootSound(String key, ...)`: 1번째(index 0) 인자가 SOUND 키로 인식됨
    * `registerBootMusic(String key, ...)`: 1번째(index 0) 인자가 MUSIC 키로 인식됨
    * `setStringKey(String key, ...)`: 1번째(index 0) 인자가 FW 베이스 키로 인식됨
    * `registerSprite(Object source, String key)`: 2번째(index 1) 인자가 SPRITE 키로 인식됨
    * `loadTexture(..., String key, ..., String eventKey)`: 2번째(index 1) 인자는 TEXTURE, 4번째(index 3) 인자는 EVENT 키로 인식됨
    * `loadSound(..., String key, ..., String eventKey)`: 2번째(index 1) 인자는 SOUND, 4번째(index 3) 인자는 EVENT 키로 인식됨
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3번째(index 2) 인자는 MUSIC, 5번째(index 4) 인자는 EVENT 키로 인식됨

2. **Resource Usage Methods**
    * `getTexture(String key)`: 1번째(index 0) 인자가 TEXTURE 키로 인식됨
    * `getSound(String key)`: 1번째(index 0) 인자가 SOUND 키로 인식됨
    * `getMusic(String key)`: 1번째(index 0) 인자가 MUSIC 키로 인식됨
    * `getUV(String key)`: 1번째(index 0) 인자가 SPRITE 키로 인식됨
    * `event(String key)`: 1번째(index 0) 인자가 EVENT 키로 인식됨
    * `free(String type, String key)`: 1번째 인자에 "TEXTURE", "SOUND", "MUSIC" 문자열이 포함될 경우 2번째(index 1) 인자가 해당 리소스 키로 인식됨

> 참고: `Fw.add()` 및 `Fw.get()`은 `com.fw.main.Fw` 호출 여부를 검증하므로 일반 클래스의 `add()`, `get()` 메서드와는 충돌하지 않습니다.

---

## Recommended VM Options

* Force DirectX
```
-Dsun.java2d.d3d=true
```

* Force OpenGL
```
-Dsun.java2d.opengl=true
```

* Force OpenGL FBO
```
-Dsun.java2d.opengl.fbobject=true
```

* Disable Legacy DirectDraw
```
-Dsun.java2d.noddraw=true
```

* Reset VRAM Cache Acceleration Threshold
```
-Dsun.java2d.accthreshold=0
```

* Enable ZGC (Sub-millisecond Latency)
```
-XX:+UseZGC
```

* Enable Generational ZGC
```
-XX:+ZGenerational
```

---

## Life Cycle

1. **Bootstrapping (Main Thread)**: `Core` 설정 검증, 윈도우 생성, 렌더/로직 스레드 실행
2. **Async Loading (`Async-Loader Thread`)**: 시스템 모듈 구성, 부팅 에셋 바인딩, 세이브 데이터 로드, 초기 씬 실행
3. **Multi-Threaded Loop**: 로직 스레드(`update(dt)`)와 렌더 스레드(`render()`) 분리 구동
4. **Scene Lifecycle**: `changeScene()` 호출 시 기존 씬 `dispose()` -> 가비지 큐 수거 -> 신규 씬 비동기 로딩
5. **Safe Shutdown**: 세이브 데이터 동기화, 워커 스레드 Join, 윈도우 및 그래픽스 자원 반납

---

## Known Issues & Tips

* <s>**Unstable Sound Engine**
    * `ogg` 확장자 사용 불능
    * 스트림 옵션이 아닌 `Music` 사용 불능
    * 모노 채널 사운드가 스트림이 아닌 메모리에 올라갈 때 깨짐 현상 발생</s>

---

* **Unstable API**
    * 완벽하지 않은 캡슐화
    * 일부 불안정한 API 구성
---
# Olen Engine (0.2.0)

Java/AWT-pohjainen kevyt 2D-pelimoottori. Tukee suorituskykyistä muistinhallintaa, dynaamista tekstuuriatlasten pakkausta, valmista hangul-syötemoduulia (korean kieli) sekä resurssien merkkijonoavaimien staattista analyysiä ja IDE-tukea.

---

# CHANGE LOG

## PRE 0.2.0
* Resurssiavainten staattinen indeksointi ja IntelliJ IDEA -laajennuksen (`OlenEngineSupport`) tuki
    * Merkkijonoresurssien avainten (`TEXTURE`, `SOUND`, `MUSIC`, `SPRITE`, `FW`, `EVENT`) automaattinen täydennys, määrittelyyn siirtyminen, reaaliaikainen virheentarkistus ja massanimenvaihto
* Resurssinhallinnan API-standardointi ja asset-putki
    * Asynkronisen asset-latauksen valmistumisen tapahtuma-avaimen (`EVENT`) tuki
    * Eksplisiittinen resurssien vapautus-API: `free(TYPE, key)`
    * Globaalin viitekehyskontekstin `com.fw.main.Fw` dedikoitu avainindeksointi
    * `Safe Runtime` -tilan käyttöönotto: estää poikkeusten aiheuttamat kaatumiset latausvirheissä korvaavalla tekstuurilla (`wrongTexture`), tukee asset-altaan automaattista laajentamista
* Parannettu vakaus ja elinkaari
    * Poistettu kilpatilanteiden (race condition) mahdollisuus kohtausten vaihdossa ja sammutuksessa
* Renderöintiputki ja näytön hallinta
    * Uusi CRT-varjostin ja simulaatio (`RenderingOption.CRT`)
    * Yksinomaisen kokoruututilan (FSEM) ja näyttötilan (resoluutio, värisyvyys, virkistystaajuus) dynaaminen vaihto
    * Virtuaalisen näytön resoluution (`virtualScreenWidth`, `virtualScreenHeight`) mukautettu määritys
    * Moottorin mukautetun laitteistokohdistimen (`EngineCursor.png`) määritystuki
    * Kokeellisen piirtokutsujen välimuistijärjestelmän (`RenderingOption.EXPERIMENTAL`, `Call`, `LazyCall`, `StaticCall`) poisto
* Kehysajoitus (Frame Timing)
    * Tarkkojen kehyksen synkronoinnin odotustilojen (`WaitMode`: `OS_SLEEP`, `HYBRID`, `BUSY_WAIT`) käyttöönotto
* Syötteiden ja virheenkorjaus-/konsolijärjestelmän parannukset
    * `KeyBindingBase`-syötekäsittelyn optimointi: poistettu Swingin `ActionMap`-ylirasitus, kevennetty 65 536 staattisen avaintaulukon indeksihauksi, korjattu sidonta-asetusten käänteisyysvirhe
    * Responsiivinen konsoli-UI ja parannettu automaattisen täydennyksen navigointi: näytön resoluutioon sidottu UI-skaalaus, suositeltujen komentojen valinta nuolinäppäimillä (UP/DOWN)
    * Jatkuvasti näkyvä virheenkorjauksen HUD-näyttö

## PRE 0.1.0
* Alustusrakenteen parannukset
    * Siirtyminen oletuskonstruktoripohjaisesta alustuksesta eksplisiittiseen alustukseen (`init`)
    * Proxy-objektien palautuksen tuki `AssetInit`-käynnistyksessä
* Suorituskyvyn profilointi
    * Lisätty `PerformanceRecorder` ja `PerformanceReader`
* Ääni
    * Äänimoottorin palauttaminen `TinySound`-kirjastoon

---

## IntelliJ IDEA Plugin Guide

Monet moottorin resurssit (tekstuurit, äänet, spritet jne.) tunnistetaan merkkijonoavaimilla. Erillisen laajennuksen asentaminen on erittäin suositeltavaa kirjoitusvirheiden estämiseksi ja sujuvan koodinavigoinnin varmistamiseksi.

* Repository: https://github.com/yooncrow33/OlenEnginePlugin

### Plugin Features
1. **Auto-completion**: Tarjoaa rekisteröityjen avainten ponnahdusikkunalistan resurssien hakufunktioissa, kuten `getTexture("")`
2. **Go to Declaration (Ctrl + Click)**: Siirry suoraan käyttökohteen merkkijonosta sen rekisteröintimäärittelyyn (esim. `registerBootTexture`)
3. **Syntax / Error Inspection**: Reaaliaikaiset varoituskorostukset (punainen alleviivaus) rekisteröimättömille tai väärin kirjoitetuille avaimille
4. **Rename Refactoring (Shift + F6)**: Päivittää rekisteröintikohdan ja kaikki käyttökohteet samanaikaisesti avaimen nimeä muutettaessa

### Notice
- Testattu ainoastaan versiolla #IC-252.26199.169.

---

## Key Features

* **Advanced Graphics Pipeline**
    * Kaksoispuskuroitu renderöinti `VolatileImage`- ja `BufferStrategy`-rajapintoihin perustuen
    * Reaaliaikainen alfarajaus (Alpha Cropping) ja dynaaminen tekstuuriatlasten luonti MaxRects-algoritmilla
    * Murtoluku- ja kokonaislukupohjaisen fyysisen skaalauksen (Fractional / Integer Physical Scaling) laskenta

* **High-Performance Sound Engine (TinySound)**
    * Matalan viiveen ääniprosessointi mikseripohjaisella arkkitehtuurilla
    * Suoratoisto ja muistissa pidettävä toisto, reaaliaikainen panorointi (Pan) ja äänenvoimakkuuden säätö

* **Developer Experience & Tooling**
    * Pelinsisäinen kehittäjäkonsoli (`~`-näppäin, puupohjainen automaattinen täydennys)
    * Valmis hangul-kirjoitus ja leikepöydän kopioi/liitä-tuki (`TextModule`) AWT-ympäristössä
    * AES-salattu tiedontallennus/lataus ja asynkroninen asset-hallinta
    * Merkkijonoavaimien staattinen tarkistus ja refaktorointi `OlenEnginePlugin`-integraation avulla

---

## Requirements

* **JDK:** OpenJDK 21+ (GraalVM 21+ suositeltu)
* **OS:** Windows / macOS / Linux

---

## Getting Started

```java
public class MyGame extends Base {

    static {
        Core.setConfig(new Config.Builder("MyGameFolder") // Projektikansio luodaan polkuun ~/.MyGameFolder
                .setWindowWidth(1280) // Todellinen ikkunan leveys (virtuaalinen resoluutio voidaan määrittää erikseen)
                .setWindowHeight(720) // Todellinen ikkunan korkeus
                .setUseKoreanModule(true) // Ottaa käyttöön korean kielen syötemoduulin
                .setUseIntegerPhysicalScaling(true) // Lukitsee fyysisen skaalauksen kokonaislukuarvoihin
                .setUseEncryption(false) // Käyttää AES-salausta tallennustiedostoihin
                .build());
    }

    public MyGame() {
        super(new Builder()
                .setUseConsole(true) // Ottaa käyttöön pelinsisäisen kehittäjäkonsolin
                .setRenderingOption(RenderingOption.DEFAULT) // Renderöintiasetus (DEFAULT / CRT)
                .setCloseWindowWithKillVM(true) // Lopettaa prosessin kokonaan ikkunan sulkeutuessa
        );
    }

    @Override
    public void init(BaseInit init) {
        // Varaa ja alusta muistialtaat
        assetManager.mallocTexturePool(1000);
        assetManager.mallocLazyLoadPool(200);

        // Rekisteröi aloituskohtaus ja luo tekstuuriatlas
        init.setInitScene(new Scene.Builder(sceneInit -> {
            sceneInit.createAtlas("DEFAULTATLAS", 2, binder -> {
                binder.registerSprite(IoUtils.getGameResourceStream("sample.png"), "sample_key");
            });
        }).setName("MAIN_SCENE").build());
    }

    @Override
    public void update(double dt) {
        // Pelilogiikan päivityssilmukka
    }

    @Override
    public void render(Graphics2D g) {
        // Renderöintiputki
        g.setColor(Color.WHITE);
        g.drawString("Olen Engine 0.2.0", 50, 50);
    }

    public static void main(String[] args) {
        new MyGame().launch();
    }
}
```

---

## Resource Key System & Code Examples

Laajennus ja moottori tunnistavat avaintyypit analysoimalla määritettyjä metodikutsuja ja parametrien sijainteja (indeksejä).

### 1. Texture (TEXTURE)
Tukee käynnistyksen aikaista rekisteröintiä, suorituksenaikaista asynkronista latausta ja instanssien hakua.

```java
// Määrittely 1: Käynnistysrekisteröinti (1. parametri: Avain)
assetInit.registerBootTexture("tex_player", IoUtils.getGameResourceStream("player.png"));

// Määrittely 2: Suorituksenaikainen asynkroninen lataus (2. parametri: Avain, 4. parametri: Valmistumistapahtuman avain)
assetManager.loadTexture("assets/bg.png", "tex_stage_bg", 0, "EVENT_BG_LOAD");

// Käyttö: Tekstuuri-instanssin haku (1. parametri: Kohdeavain)
Texture tex = assetManager.getTexture("tex_player");
```

### 2. Sound Effects (SOUND)
Käsittelee lyhyiden ääniefektitiedostojen rekisteröintiä ja toistoa.

```java
// Määrittely 1: Käynnistysrekisteröinti (1. parametri: Avain)
assetInit.registerBootSound("snd_hit", IoUtils.getGameResourceStream("hit.wav"));

// Määrittely 2: Suorituksenaikainen asynkroninen lataus (2. parametri: Avain, 4. parametri: Valmistumistapahtuman avain)
assetManager.loadSound("assets/jump.wav", "snd_jump", 1, "EVENT_JUMP_LOAD");

// Käyttö: Äänen haku (1. parametri: Kohdeavain)
Sound snd = assetManager.getSound("snd_hit");
snd.play();
```

### 3. Music (MUSIC)
Hallitsee suoratoistettavia taustamusiikkiresursseja (BGM).

```java
// Määrittely 1: Käynnistysrekisteröinti (1. parametri: Avain)
assetInit.registerBootMusic("bgm_main", IoUtils.getGameResourceStream("title.ogg"));

// Määrittely 2: Suorituksenaikainen asynkroninen lataus (3. parametri: Avain, 5. parametri: Valmistumistapahtuman avain)
assetManager.loadMusic("assets/stage1.ogg", StreamType.FILE, "bgm_stage1", 1.0f, "EVENT_STAGE1_LOAD");

// Käyttö: Musiikin haku (1. parametri: Kohdeavain)
Music music = assetManager.getMusic("bgm_main");
music.play(true);
```

### 4. Sprite Atlas (SPRITE)
Rekisteröi alikuvia atlas-rakentajan sisällä ja hakee UV-koordinaatit.

```java
// Määrittely: registerSprite (2. parametri: Sprite-avain)
sceneInit.createAtlas("GAME_ATLAS", 2, binder -> {
    binder.registerSprite(IoUtils.getGameResourceStream("btn_play.png"), "spr_btn_play");
});

// Käyttö: UV-koordinaattien haku (1. parametri: Kohdeavain)
UVRegion uv = assetManager.getUV("spr_btn_play");
```

### 5. Framework Base (FW)
Kartoittaa globaalin tilavaraston avaimet luokassa `com.fw.main.Fw`.

```java
import com.fw.main.Fw;

// Määrittely: setStringKey tai Fw.add (1. parametri: Avain)
Fw.setStringKey("CONF_LOCALE", "ko_KR");
Fw.add("GLOBAL_VOLUME", 0.8f);

// Käyttö: Fw.get (1. parametri: Avain)
Object conf = Fw.get("CONF_LOCALE");
```

### 6. Events (EVENT)
Tapahtuma-avaimet, joita kuunnellaan asynkronisen latauksen jälkeen tai laukaistaan eksplisiittisesti.

```java
// Määrittely: Rekisteröidään tapahtumaparametreinä metodeissa kuten loadTexture, loadSound, loadMusic

// Käyttö: Tapahtuman lähetys tai käsittelijän haarautus (1. parametri: Tapahtuma-avain)
eventManager.event("EVENT_STAGE_CLEAR");

public void onEvent(String evt) {
    if ("EVENT_BG_LOAD".equals(evt)) {
        // Käsittely latauksen valmistumisen jälkeen
    }
}
```

### 7. Resource Disposal (Free)
Vapauttaa tietyn resurssin muistista antamalla tyyppitunnisteen ja avaimen.

```java
// Käyttö: free (1. parametri: Tyypin nimi, 2. parametri: Resurssiavain)
assetManager.free("TEXTURE", "tex_stage_bg");
assetManager.free("SOUND", "snd_jump");
assetManager.free("MUSIC", "bgm_stage1");
```

---

## Prohibited Conflicting Method Declarations

Nopean analyysin ja indeksointivaiheen (Dumb Mode) tukemiseksi IDE-laajennus ei jäljitä kutsuttavan luokan tyyppiä joka kerta tarkasti, vaan tunnistaa resurssiavaimet **metodin nimen ja parametrien sijaintien (indeksien)** perusteella.

Siksi, jos käyttäjän omissa luokissa määritellään metodeja, joilla on sama nimi ja merkkijonoparametrien järjestys kuin alla on esitetty, tavalliset merkkijonot saatetaan tulkita virheellisesti moottorin resurssiavaimiksi. Tämä voi johtaa virheellisiin punaisiin alleviivauksiin, odottamattomiin täydennysvalikoihin tai tahattomiin muutoksiin massanimenvaihdon (Shift + F6) aikana.

### Conflict-Prone Method List

1. **Resource Definition Methods**
    * `registerBootTexture(String key, ...)`: 1. parametri (index 0) tunnistetaan TEXTURE-avaimeksi
    * `registerBootSound(String key, ...)`: 1. parametri (index 0) tunnistetaan SOUND-avaimeksi
    * `registerBootMusic(String key, ...)`: 1. parametri (index 0) tunnistetaan MUSIC-avaimeksi
    * `setStringKey(String key, ...)`: 1. parametri (index 0) tunnistetaan FW-pohja-avaimeksi
    * `registerSprite(Object source, String key)`: 2. parametri (index 1) tunnistetaan SPRITE-avaimeksi
    * `loadTexture(..., String key, ..., String eventKey)`: 2. parametri (index 1) tunnistetaan TEXTURE-, 4. parametri (index 3) EVENT-avaimeksi
    * `loadSound(..., String key, ..., String eventKey)`: 2. parametri (index 1) tunnistetaan SOUND-, 4. parametri (index 3) EVENT-avaimeksi
    * `loadMusic(..., ..., String key, ..., String eventKey)`: 3. parametri (index 2) tunnistetaan MUSIC-, 5. parametri (index 4) EVENT-avaimeksi

2. **Resource Usage Methods**
    * `getTexture(String key)`: 1. parametri (index 0) tunnistetaan TEXTURE-avaimeksi
    * `getSound(String key)`: 1. parametri (index 0) tunnistetaan SOUND-avaimeksi
    * `getMusic(String key)`: 1. parametri (index 0) tunnistetaan MUSIC-avaimeksi
    * `getUV(String key)`: 1. parametri (index 0) tunnistetaan SPRITE-avaimeksi
    * `event(String key)`: 1. parametri (index 0) tunnistetaan EVENT-avaimeksi
    * `free(String type, String key)`: Jos 1. parametri sisältää merkkijonon "TEXTURE", "SOUND" tai "MUSIC", 2. parametri (index 1) tunnistetaan kyseiseksi resurssiavaimeksi

> Huomautus: `Fw.add()` ja `Fw.get()` tarkistavat suoran `com.fw.main.Fw`-kutsun, joten ne eivät aiheuta ristiriitoja yleisten luokkien `add()`- ja `get()`-metodien kanssa.

---

## Recommended VM Options

* Pakota DirectX
```
-Dsun.java2d.d3d=true
```

* Pakota OpenGL
```
-Dsun.java2d.opengl=true
```

* Pakota OpenGL FBO
```
-Dsun.java2d.opengl.fbobject=true
```

* Ota pois käytöstä vanha DirectDraw
```
-Dsun.java2d.noddraw=true
```

* Nollaa VRAM-välimuistin kiihdytyksen kynnysarvo
```
-Dsun.java2d.accthreshold=0
```

* Ota käyttöön ZGC (Alimillisekunnin viive)
```
-XX:+UseZGC
```

* Ota käyttöön Generational ZGC
```
-XX:+ZGenerational
```

---

## Life Cycle

1. **Bootstrapping (Pääsäie / Main Thread)**: Tarkistaa `Core`-määritykset, luo ikkunan ja käynnistää renderöinti-/logiikkasäikeet
2. **Async Loading (`Async-Loader Thread`)**: Määrittää järjestelmämoduulit, sitoo käynnistysresurssit, lataa tallennustiedot ja aktivoi aloituskohtauksen
3. **Multi-Threaded Loop**: Suorittaa logiikkasäiettä (`update(dt)`) ja renderöintisäiettä (`render()`) rinnakkain
4. **Scene Lifecycle**: Kun kutsutaan `changeScene()`, edellinen kohtaus vapautetaan (`dispose()`) -> roskakori kerätään -> uusi kohtaus ladataan asynkronisesti
5. **Safe Shutdown**: Synkronoi tallennustiedot, yhdistää työsäikeet (join) sekä vapauttaa ikkuna- ja grafiikkaresurssit

---

## Known Issues & Tips

* <s>**Unstable Sound Engine**
    * `.ogg`-tiedostopäätettä ei voi käyttää
    * `Music`-toistoa ei voi käyttää ilman stream-valintaa
    * Monokanavaääni vääristyy, kun se ladataan suoratoiston sijaan muistiin</s>

---

* **Unstable API**
    * Kapselointi ei ole täydellistä
    * Osa API-rakenteista on toistaiseksi epävakaita