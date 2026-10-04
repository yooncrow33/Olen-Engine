package com.fw.main;

import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.input.mouse.MouseInterface;
import com.fw.main.utils.platform.system.scene.Scene;
import com.fw.main.utils.platform.system.performance.GraphTab;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

public class PerformanceReader extends Base {
    private final List<GraphTab> tabList = new ArrayList<>();
    private int currentTab = 0;

    private static final int GRAPH_X = 40;
    private static final int GRAPH_Y = 85;
    private static final int GRAPH_WIDTH = 1840;
    private static final int GRAPH_HEIGHT = 955;

    // 드래그 델타 계산용 이전 좌표 (가상 좌표계)
    private int lastDragVX = 0;
    private int lastDragVY = 0;

    static {
        Core.setConfig(new Config.Builder("Performance Data Reader")
                .setWindowWidth(1920)
                .setWindowHeight(1080)
                .setUseKoreanModule(true)
                .setUseIntegerPhysicalScaling(true)
                .setEncryptionKey("keyforencryption")
                .setUseEncryption(false)
                .build()
        );
    }

    public PerformanceReader() {
        super(new Builder()
                .setIntegerKey(1)
                .setStringKey("1")
                .setCrtJitterEnabled(true)
                .setUseConsole(true)
                .setRenderingOption(RenderingOption.CRT)
        );
    }

    public static class ProfileDataResult {
        public final Map<String, Long[]> dataMap = new LinkedHashMap<>();
        public final Map<Integer, String> phaseMap = new TreeMap<>();
        public final Map<String, String> sysInfoMap = new LinkedHashMap<>();
    }

    public static ProfileDataResult loadProfileFromStream(InputStream in) throws IOException {
        ProfileDataResult result = new ProfileDataResult();
        Properties prop = new Properties();
        prop.load(in);

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("sysinfo.")) {
                result.sysInfoMap.put(key.substring(8), prop.getProperty(key));
            }
        }

        String phaseStr = prop.getProperty("phases", "");
        if (!phaseStr.isEmpty()) {
            String[] entries = phaseStr.split(";");
            for (String entry : entries) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    result.phaseMap.put(Integer.parseInt(kv[0]), kv[1]);
                }
            }
        }

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("data.")) {
                String metricName = key.substring(5);
                String rawValues = prop.getProperty(key);
                if (rawValues == null || rawValues.isEmpty()) {
                    result.dataMap.put(metricName, new Long[0]);
                    continue;
                }

                String[] tokens = rawValues.split(",");
                Long[] longArray = new Long[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    longArray[i] = Long.parseLong(tokens[i].trim());
                }
                result.dataMap.put(metricName, longArray);
            }
        }
        return result;
    }

    @Override
    public void init(BaseInit init) {
        assetManager.mallocTexturePool(10);
        assetManager.mallocLazyLoadPool(10);

        init.setInitScene(new Scene.Builder(sceneInit -> {}).setName("DEFAULT").build());

        FileDialog dialog = new FileDialog((Frame) null, "select data(.fwD)", FileDialog.LOAD);
        dialog.setFile("*.fwD");
        dialog.setVisible(true);

        String dir = dialog.getDirectory();
        String file = dialog.getFile();

        if (dir != null && file != null) {
            File selectedFile = new File(dir, file);
            try (InputStream in = new FileInputStream(selectedFile)) {
                ProfileDataResult profile = loadProfileFromStream(in);

                for (Map.Entry<String, Long[]> entry : profile.dataMap.entrySet()) {
                    tabList.add(new GraphTab(entry.getKey(), entry.getValue(), profile.phaseMap, profile.sysInfoMap));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private int toVirtualX(int physicalX) {
        double scale = getViewScale();
        return (scale > 0) ? (int) (physicalX / scale) : physicalX;
    }

    private int toVirtualY(int physicalY) {
        double scale = getViewScale();
        return (scale > 0) ? (int) (physicalY / scale) : physicalY;
    }

    @Override
    public void setMouse(Mouse mouse) {
        // 엔진 고유의 MouseInterface만을 사용하여 완벽 구동
        mouse.registerMouseInterface(new MouseInterface() {
            @Override
            public void mouseClicked(FwMouseAPI e) {
                int vx = toVirtualX(e.getX());
                int vy = toVirtualY(e.getY());

                // 상단 메인 탭 전환
                int startX = GRAPH_X;
                for (int i = 0; i < tabList.size(); i++) {
                    if (vx >= startX && vx <= startX + 180 && vy >= 35 && vy <= 70) {
                        currentTab = i;
                        return;
                    }
                    startX += 190;
                }

                // Phase 탭 및 RST CAP 버튼
                if (!tabList.isEmpty()) {
                    tabList.get(currentTab).onSubTabClick(vx, vy, GRAPH_X, GRAPH_Y, GRAPH_HEIGHT);
                }
            }

            @Override
            public void mousePressed(FwMouseAPI e) {
                lastDragVX = toVirtualX(e.getX());
                lastDragVY = toVirtualY(e.getY());
            }

            @Override
            public void mouseReleased(FwMouseAPI e) {
            }

            @Override
            public void mouseDragged(FwMouseAPI e) {
                if (tabList.isEmpty()) return;

                int curVX = toVirtualX(e.getX());
                int curVY = toVirtualY(e.getY());

                int dx = curVX - lastDragVX;
                int dy = curVY - lastDragVY;

                GraphTab tab = tabList.get(currentTab);

                // 엔진 API(isRightButton, isLeftButton)를 통해 드래그 중인 버튼 판별
                if (e.isRightButton()) {
                    if (dy != 0) {
                        tab.adjustCap(dy);
                    }
                } else if (e.isLeftButton()) {
                    if (dx != 0) {
                        tab.pan(dx, GRAPH_WIDTH);
                    }
                }

                lastDragVX = curVX;
                lastDragVY = curVY;
            }

            @Override public void mouseEntered(FwMouseAPI e) {}
            @Override public void mouseExited(FwMouseAPI e) {}

            @Override
            public void mouseWheelMoved(FwMouseAPI e) {
                if (tabList.isEmpty()) return;
                int vx = toVirtualX(e.getX());
                int rot = e.getWheelRotation();

                if (e.isShiftDown()) {
                    float factor = (rot < 0) ? 0.85f : 1.15f;
                    tabList.get(currentTab).adjustCapByFactor(factor);
                } else {
                    tabList.get(currentTab).zoom(rot, vx, GRAPH_WIDTH);
                }
            }
        });
    }

    @Override
    public void update(double dt) {}

    @Override
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int tabX = GRAPH_X;
        for (int i = 0; i < tabList.size(); i++) {
            boolean isSelected = (currentTab == i);
            g.setColor(isSelected ? new Color(65, 75, 105) : new Color(35, 36, 45));
            g.fillRect(tabX, 35, 180, 35);
            g.setColor(isSelected ? Color.CYAN : Color.LIGHT_GRAY);
            g.drawRect(tabX, 35, 180, 35);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(tabList.get(i).getTitle(), tabX + 10, 57);
            tabX += 190;
        }

        if (!tabList.isEmpty()) {
            tabList.get(currentTab).render(g, GRAPH_X, GRAPH_Y, GRAPH_WIDTH, GRAPH_HEIGHT);
        } else {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString("No data selected.", GRAPH_X + 20, GRAPH_Y + 40);
        }
    }

    public static void main(String[] args) {
        new PerformanceReader().launch();
    }
}