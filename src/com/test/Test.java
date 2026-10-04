package com.test;

import com.fw.main.*;
import com.fw.main.api.io.DynamicIoLoadObject;
import com.fw.main.api.io.IoInterface;
import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.input.korean.TextObject;
import com.fw.main.utils.input.korean.TextObjectEventListener;
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.input.mouse.MouseInterface;
import com.fw.main.utils.io.IoUtils;
import com.fw.main.utils.platform.system.asset.AssetManager;
import com.fw.main.utils.platform.system.asset.Texture;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.MusicAsset;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.SoundAsset;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;
import com.fw.main.utils.platform.system.scene.Atlas;
import com.fw.main.utils.platform.system.scene.Bgm;
import com.fw.main.utils.platform.system.scene.Scene;
import com.fw.main.utils.platform.system.scene.Sfx;
import com.fw.main.utils.platform.system.scene.Sprite;

import java.awt.*;
import java.util.Properties;

public class Test extends Base {
    TextObject ko = new TextObject();
    float updatable;

    Sfx mouseClickSound;
    Bgm bgm;
    Sprite sprHeroIdle;
    Sprite sprHeroWalk;
    Sprite sprEnemyBoss;
    Atlas testAtlas;

    static {
        Core.setConfig(new Config.Builder("Olen Engine")
                .setWindowWidth(1280)
                .setWindowHeight(720)
                .setUseKoreanModule(true)
                .setUseIntegerPhysicalScaling(true)
                .setLoadingScreenTexture(IoUtils.getEngineResourceStream("yourLoadingScreen.png"))
                .setEncryptionKey("keyforencryption")
                .setSafeRuntime(true)
                .setUseEncryption(false)
                .build()
        );
    }

    public Test() {
        super(new Builder()
                .setIntegerKey(1)
                // [카테고리 3: Fw Key 등록 1]
                .setStringKey("sdf")
                .setUseConsole(true)
                .setUseEngineCursor(true)
                .setCloseWindowWithKillVM(false)
                .setPerformanceRecorderOption(PerformanceRecorder.CaptureMode.EVERY_FRAME, "test")
                .setRenderingOption(RenderingOption.LEGACY)
        );
    }

    @Override
    public void setConsole(Base.ConsoleInit c) {
        c.registerConsoleCMD(args -> {
            if ("trigger_lazy".equals(args.get(0))) {
                // [카테고리 5: 이벤트 트리거 호출] "EVENT_" 입력 시 자동완성 및 Ctrl+Click 이동 확인
                assetManager.event("EVENT_STAGE_01_RESOURCES");
                assetManager.event("EVENT_BOSS_RESOURCES");
            }
        });

        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("not_engine_key");

        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.get("not_engine_key");
    }

    @Override
    public void setMouse(Mouse mouse) {
        mouse.registerMouseInterface(new MouseInterface() {
            @Override
            public void mouseClicked(FwMouseAPI e) {
                // [카테고리 1: 사운드 재생 조회]
                if (assetManager.getSound("snd_click") != null) {
                    assetManager.getSound("snd_click").play();
                }
            }

            @Override public void mousePressed(FwMouseAPI e) {}
            @Override public void mouseReleased(FwMouseAPI e) {}
            @Override public void mouseEntered(FwMouseAPI e) {}
            @Override public void mouseExited(FwMouseAPI e) {}
            @Override public void mouseWheelMoved(FwMouseAPI e) {}
        });
    }

    @Override
    public void init(BaseInit init) {
        new TestBindingLegacy(this);

        // =========================================================
        // [카테고리 3: Fw 싱글톤 등록]
        // =========================================================
        Fw.add("SUB_WINDOW_KEY", this);

        // =========================================================
        // [카테고리 1 & 5: Boot 에셋 등록 (Texture, Sound, Music)]
        // =========================================================
        AssetInit assetInit = init.getAssetInit();
        assetInit.registerBootTexture("tex_logo_olen", IoUtils.getEngineResourceStream("Olen.png"));
        assetInit.registerBootTexture("tex_bg_title", IoUtils.getEngineResourceStream("Title.png"));
        assetInit.registerBootTexture("tex_dummy_pixel", null);

        assetInit.registerBootSound("snd_click", IoUtils.getGameResourceStream("click.wav"));
        assetInit.registerBootSound("snd_explosion", IoUtils.getGameResourceStream("boom.wav"));

        assetInit.registerBootMusic("bgm_title", IoUtils.getGameResourceStream("title_theme.wav"));
        assetInit.registerBootMusic("bgm_dungeon", IoUtils.getGameResourceStream("dungeon_theme.wav"));

        // =========================================================
        // [카테고리 1 & 5: 동적 로딩(LAZY) 및 이벤트 그룹 등록]
        // =========================================================
        assetManager.mallocTexturePool(20);
        assetManager.mallocLazyLoadPool(20);

        // loadTexture(LoadMode, assetKey, InputStream, eventKey)
        assetManager.loadTexture(AssetManager.LoadMode.LAZY, "tex_stage1_ground",
                IoUtils.getGameResourceStream("ground.png"), "EVENT_STAGE_01_RESOURCES");
        assetManager.loadTexture(AssetManager.LoadMode.LAZY, "tex_stage1_sky",
                IoUtils.getGameResourceStream("sky.png"), "EVENT_STAGE_01_RESOURCES");

        // loadSound(LoadMode, assetKey, InputStream, eventKey)
        assetManager.loadSound(AssetManager.LoadMode.LAZY, "snd_boss_roar",
                IoUtils.getGameResourceStream("roar.wav"), "EVENT_BOSS_RESOURCES");

        // loadMusic(LoadMode, MusicType, assetKey, InputStream, eventKey)
        assetManager.loadMusic(AssetManager.LoadMode.LAZY, AssetManager.MusicType.STREAM_MUSIC, "bgm_boss_battle",
                IoUtils.getGameResourceStream("boss.wav"), "EVENT_BOSS_RESOURCES");

        // =========================================================
        // [카테고리 2: Scene Atlas 스프라이트 키 등록]
        // =========================================================
        init.setInitScene(new Scene.Builder(sceneInit -> {
            mouseClickSound = sceneInit.registerSound(IoUtils.getGameResourceStream("rr.wav"));
            bgm = sceneInit.registerMusic(IoUtils.getGameResourceStream("music.wav"), true);

            sceneInit.createAtlas("MAIN_SHEET", 2, binder -> {
                sprHeroIdle = binder.registerSprite(IoUtils.getGameResourceStream("hero_idle.png"), "spr_hero_idle");
                sprHeroWalk = binder.registerSprite(IoUtils.getGameResourceStream("hero_walk.png"), "spr_hero_walk");
                sprEnemyBoss = binder.registerSprite(IoUtils.getGameResourceStream("boss.png"), "spr_enemy_boss");
            });
        }).setName("DEFAULT").build());

        // 기타 세팅
        ko.setFocused(true);
        ko.registerKoreanObjectEventListener(new TextObjectEventListener() {
            @Override public void enter() { ko.clear(); }
            @Override public void tab() {}
        });

        init.getOperatorManager().exitOperatorPack.addOperator(() -> System.out.println("exit"));
        init.getIo().addIoObject("default", new IoInterface() {
            @Override public void save(Properties p) { p.setProperty("float", Float.toString(updatable)); }
            @Override public void load(Properties p) { updatable = Float.parseFloat((String) p.get("float")); }
            @Override public void initLoad(Properties p) { updatable = (float) Math.random(); }
        });
        new DynamicIoLoadObject("full_path", p -> {}).launch();
    }

    @Override
    public void update(double dt) {
        updatable = (float) Math.random();
    }

    @Override
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setFont(new Font("", Font.BOLD, 18));
        g.drawString("Olen Plugin Auto-Complete & Warning Test", 50, 50);

        // =========================================================================
        // 1. [정상 참조 테스트] Ctrl+Click 시 해당 선언부로 이동 & 팝업 정상 노출 확인
        // =========================================================================

        // (A) Asset Texture 조회
        Texture texLogo = assetManager.getTexture("tex_logo_olen");
        Texture texGround = assetManager.getTexture("tex_stage1_ground");

        // (B) Asset Sound & Music 조회
        SoundAsset sndBoom = (SoundAsset) assetManager.getSound("snd_explosion");
        MusicAsset bgmTitle = (MusicAsset) assetManager.getMusic("bgm_title");

        // (C) Fw 싱글톤 인스턴스 조회
        Base mainBase = Fw.get("sdf");
        Base subBase = Fw.get("SUB_WINDOW_KEY");

        // (D) Atlas Sprite UV 조회
        if (testAtlas != null) {
            float[] uvIdle = testAtlas.getUV("spr_hero_idle");
            float[] uvWalk = testAtlas.getUV("spr_hero_walk");
            float[] uvBoss = testAtlas.getUV("spr_enemy_boss");
        }

        // (E) Asset Free 키 조회 (2번째 파라미터)
        assetManager.free(AssetManager.AssetType.TEXTURE, "tex_bg_title");
        assetManager.free(AssetManager.AssetType.SOUND, "snd_boss_roar");

        // =========================================================================
        // 2. [노란색 경고 라인 테스트] 존재하지 않는 키 입력 -> Yellow Warning 발생해야 함
        // =========================================================================

        assetManager.getTexture("tex_INVALID_NOT_EXIST");      // 경고 발생 확인
        assetManager.getSound("snd_GHOST_SOUND");              // 경고 발생 확인
        assetManager.getMusic("bgm_UNKNOWN_THEME");            // 경고 발생 확인
        assetManager.event("EVENT_TYPO_EVENT_NAME");           // 경고 발생 확인
        Fw.get("FW_INVALID_KEY");                              // 경고 발생 확인
        if (testAtlas != null) {
            testAtlas.getUV("spr_UNREGISTERED_SPRITE");        // 경고 발생 확인
        }

        // =========================================================================
        // 3. [직접 타이핑 테스트] 아래 빈 따옴표("") 안에 커서를 두고 타이핑해보기
        // =========================================================================
        // (1) assetManager.getTexture("t   -> tex_logo_olen, tex_bg_title, tex_stage1_ground 추천 확인
        // (2) Fw.get("M                   -> MAIN_TEST_INSTANCE 추천 확인
        // (3) testAtlas.getUV("s          -> spr_hero_idle, spr_hero_walk, spr_enemy_boss 추천 확인
        // (4) assetManager.event("E       -> EVENT_STAGE_01_RESOURCES, EVENT_BOSS_RESOURCES 추천 확인

        assetManager.getTexture("");
        Fw.get("s");
        assetManager.event("");
        assetManager.getMusic("");
        assetManager.getSound("");
    }

    public static void main(String[] args) {
        new Test().launch();
    }
}