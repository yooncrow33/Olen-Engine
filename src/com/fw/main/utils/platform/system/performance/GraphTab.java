package com.fw.main.utils.platform.system.performance;

import com.fw.main.utils.input.mouse.FwMouseAPI;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GraphTab {
    private final String title;
    private final Long[] data;
    private final Map<Integer, String> phaseMap;
    private final Map<String, String> sysInfoMap;

    private float offsetX = 0;
    private float zoomX = 2.0f;

    private double overallAvg;
    private long overallMax = Long.MIN_VALUE;
    private long overallMin = Long.MAX_VALUE;
    private long displayMax;

    private final List<PhaseRange> phases = new ArrayList<>();
    private int selectedPhaseIndex = -1;

    private static final int AXIS_LEFT = 85;
    private static final int AXIS_BOTTOM = 25;
    private static final int PADDING_TOP = 25;
    private static final int BOTTOM_UI_HEIGHT = 160;

    public static class PhaseRange {
        public String name;
        public int startTick;
        public int endTick;
        public double avg;
        public long max = Long.MIN_VALUE;
        public long min = Long.MAX_VALUE;
        public int maxTick = -1;
        public int minTick = -1;
    }

    public GraphTab(String title, Long[] data, Map<Integer, String> phaseMap, Map<String, String> sysInfoMap) {
        this.title = title;
        this.data = (data != null) ? data : new Long[0];
        this.phaseMap = (phaseMap != null) ? phaseMap : new TreeMap<>();
        this.sysInfoMap = (sysInfoMap != null) ? sysInfoMap : new LinkedHashMap<>();
        initStatsAndPhases();
        this.displayMax = this.overallMax;
    }

    private void initStatsAndPhases() {
        if (data.length == 0) {
            overallMax = 0;
            overallMin = 0;
            overallAvg = 0;
            return;
        }

        double totalSum = 0;
        for (long val : data) {
            totalSum += val;
            if (val > overallMax) overallMax = val;
            if (val < overallMin) overallMin = val;
        }
        overallAvg = totalSum / data.length;

        List<Integer> ticks = new ArrayList<>(phaseMap.keySet());
        Collections.sort(ticks);

        for (int i = 0; i < ticks.size(); i++) {
            int start = ticks.get(i);
            int end = (i + 1 < ticks.size()) ? ticks.get(i + 1) - 1 : data.length - 1;
            if (start >= data.length) continue;
            end = Math.min(end, data.length - 1);

            PhaseRange p = new PhaseRange();
            p.name = phaseMap.get(start) + " Phase";
            p.startTick = start;
            p.endTick = end;

            double pSum = 0;
            int count = 0;
            for (int t = start; t <= end; t++) {
                long val = data[t];
                pSum += val;
                if (val > p.max) {
                    p.max = val;
                    p.maxTick = t;
                }
                if (val < p.min) {
                    p.min = val;
                    p.minTick = t;
                }
                count++;
            }
            p.avg = (count > 0) ? (pSum / count) : 0;
            phases.add(p);
        }
    }

    public void render(Graphics2D g, int x, int y, int width, int height) {
        int innerGraphX = x + AXIS_LEFT;
        int innerGraphY = y + PADDING_TOP;
        int innerGraphW = width - AXIS_LEFT;
        int innerGraphH = height - BOTTOM_UI_HEIGHT - AXIS_BOTTOM - PADDING_TOP;

        g.setColor(new Color(14, 14, 18));
        g.fillRect(x, y, width, height - BOTTOM_UI_HEIGHT);

        Shape oldClip = g.getClip();
        g.setClip(innerGraphX, y, innerGraphW, innerGraphH + PADDING_TOP);

        g.setColor(new Color(20, 20, 25));
        g.fillRect(innerGraphX, innerGraphY, innerGraphW, innerGraphH);

        for (int i = 0; i < phases.size(); i++) {
            PhaseRange p = phases.get(i);
            float startScreenX = innerGraphX + offsetX + (p.startTick * zoomX);
            float endScreenX = innerGraphX + offsetX + ((p.endTick + 1) * zoomX);

            g.setColor((i % 2 == 0) ? new Color(28, 30, 38) : new Color(23, 24, 30));
            g.fillRect((int) startScreenX, innerGraphY, (int) (endScreenX - startScreenX), innerGraphH);

            g.setColor(new Color(160, 175, 195));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(p.name, startScreenX + 8, y + 16);
        }

        int tickStep = calculateOptimalTickStep(zoomX);
        int startVisibleTick = Math.max(0, (int) (-offsetX / zoomX) - 1);
        int endVisibleTick = Math.min(data.length - 1, (int) ((-offsetX + innerGraphW) / zoomX) + 1);
        int firstAlignedTick = (startVisibleTick / tickStep) * tickStep;

        for (int t = firstAlignedTick; t <= endVisibleTick; t += tickStep) {
            if (t < 0 || t >= data.length) continue;
            int tickScreenX = (int) (innerGraphX + offsetX + (t * zoomX));
            g.setColor(new Color(50, 55, 65, 80));
            g.drawLine(tickScreenX, innerGraphY, tickScreenX, innerGraphY + innerGraphH);
        }

        int steps = 5;
        for (int i = 0; i <= steps; i++) {
            float ratio = (float) i / steps;
            int lineY = (int) (innerGraphY + innerGraphH - (ratio * innerGraphH));
            g.setColor(new Color(55, 60, 72, 110));
            g.drawLine(innerGraphX, lineY, innerGraphX + innerGraphW, lineY);
        }

        long range = displayMax - overallMin;
        if (range <= 0) range = 1;

        if (data.length > 1) {
            g.setColor(new Color(75, 220, 130));
            for (int i = 0; i < data.length - 1; i++) {
                int x1 = (int) (innerGraphX + offsetX + (i * zoomX));
                int y1 = (int) (innerGraphY + innerGraphH - ((double) (data[i] - overallMin) / range * innerGraphH));
                int x2 = (int) (innerGraphX + offsetX + ((i + 1) * zoomX));
                int y2 = (int) (innerGraphY + innerGraphH - ((double) (data[i + 1] - overallMin) / range * innerGraphH));

                y1 = Math.max(innerGraphY - 5, Math.min(innerGraphY + innerGraphH + 5, y1));
                y2 = Math.max(innerGraphY - 5, Math.min(innerGraphY + innerGraphH + 5, y2));

                if (x2 >= innerGraphX && x1 <= innerGraphX + innerGraphW) {
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            for (PhaseRange p : phases) {
                if (p.maxTick >= 0) {
                    int mx = (int) (innerGraphX + offsetX + (p.maxTick * zoomX));
                    int my = (int) (innerGraphY + innerGraphH - ((double) (p.max - overallMin) / range * innerGraphH));
                    my = Math.max(innerGraphY + 12, Math.min(innerGraphY + innerGraphH - 12, my));
                    if (mx >= innerGraphX - 50 && mx <= innerGraphX + innerGraphW + 50) {
                        drawValueBadge(g, mx, my, "MAX " + formatValue(p.max), true);
                    }
                }
                if (p.minTick >= 0) {
                    int nx = (int) (innerGraphX + offsetX + (p.minTick * zoomX));
                    int ny = (int) (innerGraphY + innerGraphH - ((double) (p.min - overallMin) / range * innerGraphH));
                    ny = Math.max(innerGraphY + 12, Math.min(innerGraphY + innerGraphH - 12, ny));
                    if (nx >= innerGraphX - 50 && nx <= innerGraphX + innerGraphW + 50) {
                        drawValueBadge(g, nx, ny, "MIN " + formatValue(p.min), false);
                    }
                }
            }
        }
        g.setClip(oldClip);

        g.setColor(new Color(70, 75, 88));
        g.drawRect(innerGraphX, innerGraphY, innerGraphW, innerGraphH);

        // Y축 수치 표시
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i <= steps; i++) {
            float ratio = (float) i / steps;
            int lineY = (int) (innerGraphY + innerGraphH - (ratio * innerGraphH));
            long val = (long) (overallMin + ratio * range);
            String valStr = formatValue(val);

            g.setColor(new Color(140, 150, 168));
            g.drawString(valStr, innerGraphX - fm.stringWidth(valStr) - 8, lineY + 4);
        }

        // Y축 단위 레이블과 겹치지 않게 그래프 상단 바깥으로 분리한 CAP 초기화 버튼
        boolean isCapModified = (displayMax != overallMax);
        g.setColor(isCapModified ? new Color(180, 60, 50) : new Color(35, 38, 46));
        g.fillRect(x + 2, y - 24, 76, 20);
        g.setColor(isCapModified ? Color.ORANGE : new Color(80, 85, 98));
        g.drawRect(x + 2, y - 24, 76, 20);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(isCapModified ? "RST CAP *" : "CAP AUTO", x + 10, y - 10);

        // X축 타임라인
        int bottomAxisY = innerGraphY + innerGraphH + 16;
        Shape bottomClip = g.getClip();
        g.setClip(innerGraphX, innerGraphY + innerGraphH, innerGraphW, AXIS_BOTTOM);

        for (int t = firstAlignedTick; t <= endVisibleTick; t += tickStep) {
            if (t < 0 || t >= data.length) continue;
            int tickScreenX = (int) (innerGraphX + offsetX + (t * zoomX));
            g.setColor(new Color(130, 140, 160));
            g.drawString(String.valueOf(t), tickScreenX - (fm.stringWidth(String.valueOf(t)) / 2), bottomAxisY);
        }
        g.setClip(bottomClip);

        // 하단 서브탭
        int subTabY = y + height - BOTTOM_UI_HEIGHT + 10;
        int subTabX = x + AXIS_LEFT;
        for (int i = 0; i < phases.size(); i++) {
            PhaseRange p = phases.get(i);
            int tabW = 120;
            g.setColor(selectedPhaseIndex == i ? new Color(65, 85, 135) : new Color(38, 40, 48));
            g.fillRect(subTabX, subTabY, tabW, 24);
            g.setColor(selectedPhaseIndex == i ? Color.CYAN : new Color(90, 95, 110));
            g.drawRect(subTabX, subTabY, tabW, 24);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(p.name, subTabX + 8, subTabY + 16);
            subTabX += tabW + 8;
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        String statLine = String.format("[METRIC STATS]  Avg: %.2f  |  Max: %s  |  Min: %s  |  Current ViewCap: %s  [Drag Right-Mouse: Up/Down to Adjust Cap | Left-Mouse: Pan]",
                overallAvg, formatValue(overallMax), formatValue(overallMin), formatValue(displayMax));
        g.drawString(statLine, subTabX + 20, subTabY + 16);

        // 하단 전체 전폭 시스템 정보 패널 (4열 그리드, 축약 없이 전체 출력)
        renderFullSystemInfoPanel(g, x, subTabY + 30, width, BOTTOM_UI_HEIGHT - 35);
    }

    private void renderFullSystemInfoPanel(Graphics2D g, int px, int py, int pw, int ph) {
        g.setColor(new Color(18, 20, 26));
        g.fillRoundRect(px, py, pw, ph, 8, 8);
        g.setColor(new Color(50, 55, 68));
        g.drawRoundRect(px, py, pw, ph, 8, 8);

        g.setColor(new Color(100, 175, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("SYSTEM, OS KERNEL, HARDWARE ARCHITECTURE & RUNTIME ENVIRONMENT METRICS", px + 14, py + 16);

        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(185, 195, 210));

        int colWidth = pw / 4;
        int col1 = px + 14;
        int col2 = px + colWidth + 5;
        int col3 = px + (colWidth * 2) + 5;
        int col4 = px + (colWidth * 3) + 5;
        int lineH = 17;
        int startY = py + 34;

        // Col 1: OS / Host / Network
        String osStr = sysInfoMap.getOrDefault("OS_NAME", "N/A") + " " + sysInfoMap.getOrDefault("OS_VERSION", "") + " (" + sysInfoMap.getOrDefault("OS_ARCH", "") + ")";
        String hostStr = sysInfoMap.getOrDefault("COMPUTER_NAME", "Unknown") + " / " + sysInfoMap.getOrDefault("HOST_IP", "127.0.0.1");
        String userStr = sysInfoMap.getOrDefault("USER_DOMAIN", "Local") + "\\" + sysInfoMap.getOrDefault("USER_NAME", "Unknown") + " (PID " + sysInfoMap.getOrDefault("PROCESS_PID", "N/A") + ")";
        String localeStr = sysInfoMap.getOrDefault("TIMEZONE", "UTC") + " | " + sysInfoMap.getOrDefault("FILE_ENCODING", "UTF-8");
        String netStr = "Adapters: " + sysInfoMap.getOrDefault("ACTIVE_NET_IF_COUNT", "1") + " Active | Session: " + sysInfoMap.getOrDefault("SESSION_NAME", "Console");

        g.drawString(truncate("OS     : " + osStr, 44), col1, startY);
        g.drawString(truncate("Host/IP: " + hostStr, 44), col1, startY + lineH);
        g.drawString(truncate("Account: " + userStr, 44), col1, startY + lineH * 2);
        g.drawString(truncate("Network: " + netStr, 44), col1, startY + lineH * 3);
        g.drawString(truncate("Locale : " + localeStr, 44), col1, startY + lineH * 4);

        // Col 2: CPU & Memory Architecture
        String cpuDesc = sysInfoMap.getOrDefault("CPU_CORES", "N/A") + " Cores | " + sysInfoMap.getOrDefault("PROCESSOR_ID", "Unknown");
        String cpuRev = "Arch Level: " + sysInfoMap.getOrDefault("PROCESSOR_LEVEL", "N/A") + " | Rev: " + sysInfoMap.getOrDefault("PROCESSOR_REV", "N/A");
        String ramStr = "Total: " + formatMemStr(sysInfoMap.get("TOTAL_PHYSICAL_MEM")) + " (Free " + formatMemStr(sysInfoMap.get("FREE_PHYSICAL_MEM")) + ")";
        String swapStr = "Total: " + formatMemStr(sysInfoMap.get("TOTAL_SWAP_MEM")) + " (Free " + formatMemStr(sysInfoMap.get("FREE_SWAP_MEM")) + ")";
        String vmStr = "Committed: " + formatMemStr(sysInfoMap.get("COMMITTED_VM"));

        g.drawString(truncate("CPU    : " + cpuDesc, 44), col2, startY);
        g.drawString(truncate("CPU Rev: " + cpuRev, 44), col2, startY + lineH);
        g.drawString(truncate("RAM    : " + ramStr, 44), col2, startY + lineH * 2);
        g.drawString(truncate("SwapMem: " + swapStr, 44), col2, startY + lineH * 3);
        g.drawString(truncate("Virtual: " + vmStr, 44), col2, startY + lineH * 4);

        // Col 3: JVM / Memory Pool / Display
        String jvmStr = sysInfoMap.getOrDefault("JAVA_VM_NAME", "Java") + " (" + sysInfoMap.getOrDefault("JAVA_VERSION", "") + ")";
        String heapStr = "Max: " + formatMemStr(sysInfoMap.get("JVM_MAX_MEMORY")) + " | Init: " + formatMemStr(sysInfoMap.get("JVM_TOTAL_MEMORY"));
        String heapFree = "Free: " + formatMemStr(sysInfoMap.get("JVM_FREE_MEMORY")) + " | JIT: " + sysInfoMap.getOrDefault("JIT_COMPILER", "None");
        String dispStr = sysInfoMap.getOrDefault("DISPLAY_INFO", "N/A") + " (" + sysInfoMap.getOrDefault("SCREEN_COUNT", "1") + " Displays)";
        String uptimeStr = "Uptime: " + sysInfoMap.getOrDefault("JVM_UPTIME", "0s") + " | Patch: " + sysInfoMap.getOrDefault("OS_PATCH", "None");

        g.drawString(truncate("JVM    : " + jvmStr, 44), col3, startY);
        g.drawString(truncate("Heap   : " + heapStr, 44), col3, startY + lineH);
        g.drawString(truncate("HeapMem: " + heapFree, 44), col3, startY + lineH * 2);
        g.drawString(truncate("Display: " + dispStr, 44), col3, startY + lineH * 3);
        g.drawString(truncate("Status : " + uptimeStr, 44), col3, startY + lineH * 4);

        // Col 4: Threads / GC / Disks
        String threadStr = "Active: " + sysInfoMap.getOrDefault("THREAD_COUNT", "0") + " | Peak: " + sysInfoMap.getOrDefault("PEAK_THREAD_COUNT", "0") + " | Daemon: " + sysInfoMap.getOrDefault("DAEMON_THREAD_COUNT", "0");
        String gcStr = sysInfoMap.getOrDefault("GC_NAMES", "N/A");
        String diskStr = sysInfoMap.getOrDefault("DISK_VOLUMES", "N/A");
        String tmpDir = "Tmp: " + sysInfoMap.getOrDefault("TMP_DIR", "N/A");
        String jvmArgs = sysInfoMap.getOrDefault("JVM_INPUT_ARGS", "None");

        g.drawString(truncate("Threads: " + threadStr, 44), col4, startY);
        g.drawString(truncate("GC Names: " + gcStr, 44), col4, startY + lineH);
        g.drawString(truncate("Disks  : " + diskStr, 44), col4, startY + lineH * 2);
        g.drawString(truncate("Temp   : " + tmpDir, 44), col4, startY + lineH * 3);
        g.drawString(truncate("JVMArgs: " + jvmArgs, 44), col4, startY + lineH * 4);
    }

    private String formatMemStr(String val) {
        if (val == null || val.isEmpty()) return "N/A";
        try {
            long b = Long.parseLong(val);
            if (b >= 1024L * 1024 * 1024) return String.format("%.2fGB", b / (1024.0 * 1024 * 1024));
            if (b >= 1024L * 1024) return String.format("%.1fMB", b / (1024.0 * 1024));
            if (b >= 1024L) return String.format("%.0fKB", b / 1024.0);
            return b + "B";
        } catch (Exception e) {
            return val;
        }
    }

    private String truncate(String text, int max) {
        return (text.length() > max) ? text.substring(0, max - 3) + "..." : text;
    }

    private void drawValueBadge(Graphics2D g, int x, int y, String text, boolean isMax) {
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(text);
        int badgeW = textW + 10;
        int badgeH = 16;

        int badgeX = x - (badgeW / 2);
        int badgeY = isMax ? (y + 6) : (y - badgeH - 6);

        g.setColor(new Color(255, 215, 0));
        g.fillOval(x - 3, y - 3, 6, 6);

        g.setColor(new Color(20, 20, 25, 230));
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);
        g.setColor(new Color(255, 215, 0));
        g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);

        g.drawString(text, badgeX + 5, badgeY + 12);
    }

    private String formatValue(long value) {
        if (title.contains("BYTES") || title.contains("HEAP")) {
            if (value >= 1024L * 1024 * 1024) return String.format("%.2fGB", value / (1024.0 * 1024 * 1024));
            if (value >= 1024L * 1024) return String.format("%.1fMB", value / (1024.0 * 1024));
            if (value >= 1024L) return String.format("%.1fKB", value / 1024.0);
            return value + "B";
        }
        if (title.contains("NS") || title.contains("WORK")) {
            if (value >= 1_000_000_000L) return String.format("%.2fs", value / 1e9);
            if (value >= 1_000_000L) return String.format("%.2fms", value / 1e6);
            if (value >= 1_000L) return String.format("%.1fus", value / 1e3);
            return value + "ns";
        }
        if (title.contains("PERCENT") || title.contains("CPU")) {
            return value + "%";
        }
        return String.valueOf(value);
    }

    private int calculateOptimalTickStep(float currentZoom) {
        float minPixelDistance = 70.0f;
        int[] stepCandidates = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000};
        for (int step : stepCandidates) {
            if (step * currentZoom >= minPixelDistance) {
                return step;
            }
        }
        return 10000;
    }

    public void pan(int deltaX, int graphWidth) {
        offsetX += deltaX;
        clampOffset(graphWidth - AXIS_LEFT);
    }

    public void adjustCap(int deltaY) {
        long currentSpan = Math.max(10, displayMax - overallMin);
        long change = (long) (deltaY * (currentSpan / 100.0));
        if (change == 0 && deltaY != 0) change = (deltaY > 0) ? 1 : -1;

        displayMax += change;
        if (displayMax <= overallMin + 1) {
            displayMax = overallMin + 1;
        }
    }

    public void onMouseWheel(FwMouseAPI e, int mouseX, int graphWidth) {
        int rot = e.getWheelRotation();
        if (rot == 0) return;

        if (e.isShiftDown()) {
            float factor = (rot < 0) ? 0.9f : 1.1f;
            long currentSpan = displayMax - overallMin;
            displayMax = overallMin + Math.max(1L, (long)(currentSpan * factor));
            return;
        }

        float oldZoom = zoomX;
        if (rot < 0) {
            zoomX = Math.min(zoomX * 1.15f, 60.0f);
        } else {
            zoomX = Math.max(zoomX / 1.15f, 0.05f);
        }
        offsetX = mouseX - (mouseX - offsetX) * (zoomX / oldZoom);
        clampOffset(graphWidth - AXIS_LEFT);
    }

    private void clampOffset(int graphInnerWidth) {
        if (data.length == 0) return;
        float totalGraphWidth = (data.length - 1) * zoomX;

        if (totalGraphWidth <= graphInnerWidth) {
            if (offsetX > 0) offsetX = 0;
            if (offsetX < graphInnerWidth - totalGraphWidth) offsetX = Math.max(0, graphInnerWidth - totalGraphWidth);
        } else {
            float minOffset = graphInnerWidth - totalGraphWidth;
            float maxOffset = 0;
            if (offsetX < minOffset) offsetX = minOffset;
            if (offsetX > maxOffset) offsetX = maxOffset;
        }
    }

    public void onSubTabClick(int mx, int my, int x, int y, int height) {
        // [RST CAP] 버튼 클릭 검사 (x + 2, y - 24, w: 76, h: 20)
        if (mx >= x + 2 && mx <= x + 78 && my >= y - 24 && my <= y - 4) {
            this.displayMax = this.overallMax;
            return;
        }

        int subTabY = y + height - BOTTOM_UI_HEIGHT + 10;
        int subTabX = x + AXIS_LEFT;

        for (int i = 0; i < phases.size(); i++) {
            int tabW = 120;
            if (mx >= subTabX && mx <= subTabX + tabW && my >= subTabY && my <= subTabY + 24) {
                selectedPhaseIndex = (selectedPhaseIndex == i) ? -1 : i;
                break;
            }
            subTabX += tabW + 8;
        }
    }

    public void zoom(int rot, int mouseX, int graphWidth) {
        float oldZoom = zoomX;
        if (rot < 0) {
            zoomX = Math.min(zoomX * 1.15f, 60.0f);
        } else {
            zoomX = Math.max(zoomX / 1.15f, 0.05f);
        }
        offsetX = mouseX - (mouseX - offsetX) * (zoomX / oldZoom);
        clampOffset(graphWidth - AXIS_LEFT);
    }

    public void adjustCapByFactor(float factor) {
        long currentSpan = displayMax - overallMin;
        displayMax = overallMin + Math.max(1L, (long)(currentSpan * factor));
    }

    public String getTitle() { return title; }
}