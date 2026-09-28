package com.fw.main.utils.platform.system.console;

import com.fw.internal.utils.Internal;
import com.fw.main.Base;
import com.fw.main.Core;
import com.fw.main.api.io.IoInterface;
import com.fw.main.utils.graphics.RU;
import com.fw.main.utils.input.korean.TextObject;
import com.fw.main.utils.input.korean.TextObjectEventListener;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;
import com.fw.main.utils.platform.system.console.autoComplete.AutoCompleteManager;
import com.fw.main.PerformanceReader;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Console {
    public enum LogType {
        ROOT("root: "),
        CONSOLE("[Console] "),
        SYSTEM("[System] "),
        ERROR("[Console] Error: "),
        @Internal
        /**
         *It's use only internal option.
         */
                SAFE_RUNTIME("[Safe_Runtime] ");

        private final String prefix;
        LogType(String prefix) { this.prefix = prefix; }
        public String get() { return prefix; }
    }

    private static final Font FONT_LOG = new Font("Consolas", Font.PLAIN, 16);
    private static final Font FONT_LOG_INDEX = new Font("Consolas", Font.PLAIN, 12);
    private static final Font FONT_PROMPT = new Font("Consolas", Font.BOLD, 18);
    private static final Font FONT_SUGGEST_TOP = new Font("Consolas", Font.BOLD, 14);
    private static final Font FONT_SUGGEST_SUB = new Font("Consolas", Font.PLAIN, 14);
    private static final Font FONT_ALL_CANDIDATES = new Font("Consolas", Font.PLAIN, 13);

    private static final Color COLOR_BG = new Color(10, 10, 10, 240);
    private static final Color COLOR_BORDER = new Color(240, 240, 240);
    private static final Color COLOR_LOG_ERROR = new Color(250, 80, 80);
    private static final Color COLOR_LOG_ROOT = Color.WHITE;
    private static final Color COLOR_LOG_SYS = Color.GREEN;
    private static final Color COLOR_LOG_SAFE_RUNTIME = Color.YELLOW;
    private static final Color COLOR_LOG_DEFAULT = Color.LIGHT_GRAY;
    private static final Color COLOR_GRAY_TEXT = Color.GRAY;

    private static final Color COLOR_PANEL_BG = new Color(20, 20, 20, 220);
    private static final Color COLOR_PANEL_BORDER = new Color(80, 80, 80);
    private static final Color COLOR_CANDIDATES_TEXT = new Color(130, 130, 130);

    private static final BasicStroke STROKE_BORDER = new BasicStroke(3f);

    private static final Font FONT_WARNING = new Font("Consolas", Font.BOLD, 12);
    private static final Color COLOR_WARN_BG = new Color(50, 20, 20, 230);
    private static final Color COLOR_WARN_BORDER = new Color(220, 80, 80);
    private static final Color COLOR_WARN_TEXT = new Color(255, 180, 180);

    final int WINDOW_WIDTH;
    final int WINDOW_HEIGHT;

    private boolean isOpen = false;
    private boolean enable = true;

    // 추가 필드: 자동완성 선택 인덱스 및 상시 화면 디버그 표시 여부
    private int selectedSuggestionIndex = 0;
    private boolean showDebugScreen = false;

    public void isNotUse() {
        enable = false;
    }

    private final List<String> logs = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxLines = 10;
    @Internal
    private final QuickPutManager quickPutManager = new QuickPutManager();
    public QuickPutManager getQuickPutManager() {
        return quickPutManager;
    }
    private AutoCompleteManager autoCompleteManager = new AutoCompleteManager();

    private final TextObject text;

    Base base;

    public Console(Base comp) {
        this.text = new TextObject();
        text.registerKoreanObjectEventListener(new TextObjectEventListener() {
            @Override
            public void enter() {
                enterAtConsole();
            }
            @Override
            public void tab() {
                handleTabCompletion();
            }
        });
        this.base = comp;
        this.targetCanvas = comp;

        WINDOW_WIDTH = comp.WINDOW_WIDTH;
        WINDOW_HEIGHT = comp.WINDOW_HEIGHT;

        comp.setFocusable(true);
        initBinding();

        getAuto().suggestAt(0,"sys");
        getAuto().suggestAt(1,"copy").whenToken(0).is("sys");
        getAuto().suggestAt(1,"drct").whenToken(0).is("sys");
        getAuto().suggestAt(1,"quickput").whenToken(0).is("sys");
        getAuto().suggestAt(2,"10").whenToken(0).is("sys").
                whenToken(1).is("copy");
        getAuto().suggestAt(2,"10").whenToken(0).is("sys").
                whenToken(1).is("drct");
        getAuto().suggestAt(2,"put").whenToken(0).is("sys")
                .whenToken(1).is("quickput");
        getAuto().suggestAt(2,"delete").whenToken(0).is("sys")
                .whenToken(1).is("quickput");
        getAuto().suggestAt(3,"key").whenToken(0).is("sys")
                .whenToken(1).is("quickput").whenToken(2).is("delete");
        getAuto().suggestAt(3,"key").whenToken(0).is("sys")
                .whenToken(1).is("quickput").whenToken(2).is("put");
        getAuto().suggestAt(4,"CMD").whenToken(0).is("sys")
                .whenToken(1).is("quickput").whenToken(2).is("put").
                whenToken(3).is("key");
        getAuto().suggestAt(2,"clear").whenToken(0).is("sys")
                .whenToken(1).is("quickput");
        getAuto().suggestAt(1,"up").whenToken(0).is("sys");
        getAuto().suggestAt(1,"down").whenToken(0).is("sys");
        getAuto().suggestAt(1,"gc").whenToken(0).is("sys");
        getAuto().suggestAt(1,"getInfo").whenToken(0).is("sys");
        getAuto().suggestAt(2,"ver").whenToken(0).is("sys").
                whenToken(1).is("getInfo");
        getAuto().suggestAt(3,"engine").whenToken(0).is("sys").
                whenToken(1).is("getInfo").whenToken(2).is("ver");
        getAuto().suggestAt(3,"sound").whenToken(0).is("sys").
                whenToken(1).is("getInfo").whenToken(2).is("ver");
        getAuto().suggestAt(3,"built").whenToken(0).is("sys").
                whenToken(1).is("getInfo").whenToken(2).is("ver");
        getAuto().suggestAt(4,"jdk").whenToken(0).is("sys").
                whenToken(1).is("getInfo").whenToken(2).is("ver")
                .whenToken(3).is("built");
        getAuto().suggestAt(4,"vm").whenToken(0).is("sys").
                whenToken(1).is("getInfo").whenToken(2).is("ver")
                .whenToken(3).is("built");
        getAuto().suggestAt(1,"exe").whenToken(0).is("sys");
        getAuto().suggestAt(2,"performanceReader").whenToken(0).is("sys").
                whenToken(1).is("exe");
        getAuto().suggestAt(2,"testInConsole").whenToken(0).is("sys").
                whenToken(1).is("exe");
        getAuto().suggestAt(3,"errorMessage").whenToken(0).is("sys").
                whenToken(1).is("exe").whenToken(2).is("testInConsole");
        getAuto().suggestAt(3,"consoleMessage").whenToken(0).is("sys").
                whenToken(1).is("exe").whenToken(2).is("testInConsole");
        getAuto().suggestAt(3,"systemMessage").whenToken(0).is("sys").
                whenToken(1).is("exe").whenToken(2).is("testInConsole");
        getAuto().suggestAt(3,"safe_runtimeMessage").whenToken(0).is("sys").
                whenToken(1).is("exe").whenToken(2).is("testInConsole");

        // sys debug 자동완성 규칙 등록
        getAuto().suggestAt(1, "debug").whenToken(0).is("sys");
        getAuto().suggestAt(2, "true").whenToken(0).is("sys").whenToken(1).is("debug");
        getAuto().suggestAt(2, "false").whenToken(0).is("sys").whenToken(1).is("debug");
        getAuto().suggestAt(2, "1").whenToken(0).is("sys").whenToken(1).is("debug");
        getAuto().suggestAt(2, "0").whenToken(0).is("sys").whenToken(1).is("debug");
        getAuto().suggestAt(3, "screen").whenToken(0).is("sys").whenToken(1).is("debug");
    }

    public boolean isOpen() { return isOpen; }
    public void toggle() {
        isOpen = !isOpen;
        text.setFocused(!text.isFocused());
        text.clear();
        selectedSuggestionIndex = 0;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
    }
    public void scrollUp(int lines) {
        int maxOffset = Math.max(0, logs.size() - maxLines);
        scrollOffset = Math.min(scrollOffset + lines, maxOffset);
    }

    public void scrollDown(int lines) {
        scrollOffset = Math.max(0, scrollOffset - lines);
    }

    public void CMD(List<String> cmd) {
        if (base.getConsoleCMD() != null) {
            base.getConsoleCMD().CMD(cmd);
        }
    }

    public AutoCompleteManager getAuto() { return autoCompleteManager; }

    @Internal
    public void enterAtConsole() {
        String input = text.getInputText().trim();
        if (input.isEmpty()) { text.clear(); return; }
        if (hasConsecutiveSpaces(input)) {
            addLog(LogType.ERROR, "multiple consecutive spaces detected.");
            return;
        }
        if (hasSyntaxError(input)) {
            addLog(LogType.ERROR, "Unclosed quotes detected in command.");
            return;
        }

        List<String> args = parseBuffer(input, false);
        if (args.isEmpty()) { return; }

        if (args.get(0) != null && args.get(0).equals("sys")) {
            internalCMD(args);
            return;
        }

        logs.add(String.format("[%tT] %s%s", System.currentTimeMillis(), LogType.ROOT.get(), input));
        scrollOffset = 0;
        CMD(args);
        text.clear();
        selectedSuggestionIndex = 0;
    }

    @Internal
    public boolean hasConsecutiveSpaces(String sb) {
        if (sb == null) return false;
        boolean inQuotes = false;
        for (int i = 0; i < sb.length() - 1; i++) {
            char c = sb.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && c == ' ' && sb.charAt(i + 1) == ' ') {
                return true;
            }
        }
        return false;
    }

    @Internal
    public boolean hasSyntaxError(String sb) {
        if (sb == null) return false;
        boolean inQuotes = false;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '"') {
                inQuotes = !inQuotes;
            }
        }
        return inQuotes;
    }

    @Internal
    public List<String> parseBuffer(String sb, boolean preserveTrailingEmpty) {
        if (sb == null || sb.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentToken.length() > 0) {
                    result.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            result.add(currentToken.toString());
        } else if (preserveTrailingEmpty && (sb.endsWith(" ") || sb.endsWith("\""))) {
            result.add("");
        }

        return result;
    }

    public void addLog(LogType type, String message) {
        logs.add(String.format("[%tT] %s%s", System.currentTimeMillis(), type.get(), message));
        scrollOffset = 0;
    }

    @Internal
    public void render(Graphics g) {
        if (g == null || (!isOpen && !showDebugScreen)) return;
        Graphics2D g2 = (Graphics2D) g;

        final int vW = (base != null && base.WINDOW_WIDTH > 0) ? base.WINDOW_WIDTH : 1920;
        final int vH = (base != null && base.WINDOW_HEIGHT > 0) ? base.WINDOW_HEIGHT : 1080;
        final float resScale = Math.min(1.0f, Math.max(0.6f, (float) vH / 1080f));
        Font fontWarning = FONT_WARNING.deriveFont(12f * resScale);

        if (isOpen) {
            final int minConsoleH = (int) (220 * resScale);
            final int consoleH = Math.min(vH - 20, Math.max(minConsoleH, (int) (vH * 0.35f)));

            Font fontLog = FONT_LOG.deriveFont(16f * resScale);
            Font fontLogIndex = FONT_LOG_INDEX.deriveFont(12f * resScale);
            Font fontPrompt = FONT_PROMPT.deriveFont(18f * resScale);
            Font fontSuggestTop = FONT_SUGGEST_TOP.deriveFont(14f * resScale);
            Font fontSuggestSub = FONT_SUGGEST_SUB.deriveFont(14f * resScale);

            // 콘솔 배경 및 하단 테두리
            g2.setColor(COLOR_BG);
            g2.fillRect(0, 0, vW, consoleH);

            g2.setColor(COLOR_BORDER);
            g2.setStroke(STROKE_BORDER);
            g2.drawLine(0, consoleH, vW, consoleH);

            // 프롬프트 및 로그 위치 계산
            final int promptBottomMargin = (int) (22 * resScale);
            final int promptY = consoleH - promptBottomMargin;
            final int startY = (int) (32 * resScale);

            g2.setFont(fontLog);
            FontMetrics fmLog = g2.getFontMetrics();
            final int lineHeight = Math.max(fmLog.getHeight(), (int) (20 * resScale));

            final int availableHeight = (promptY - (int) (25 * resScale)) - startY;
            this.maxLines = Math.max(1, availableHeight / lineHeight);

            int totalLogs = logs.size();
            int endIndex = totalLogs - scrollOffset;
            int startIndex = Math.max(0, endIndex - maxLines);
            int lineCount = 0;

            for (int i = startIndex; i < endIndex; i++) {
                String line = logs.get(i);
                int currentY = startY + (lineCount * lineHeight);

                g2.setFont(fontLog);
                if (line.contains("Error")) g2.setColor(COLOR_LOG_ERROR);
                else if (line.contains("[Safe_Runtime]")) g2.setColor(COLOR_LOG_SAFE_RUNTIME);
                else if (line.contains("root:")) g2.setColor(COLOR_LOG_ROOT);
                else if (line.contains("[System]")) g2.setColor(COLOR_LOG_SYS);
                else g2.setColor(COLOR_LOG_DEFAULT);

                g2.drawString(line, (int) (25 * resScale), currentY);

                g2.setFont(fontLogIndex);
                g2.setColor(COLOR_GRAY_TEXT);
                g2.drawString(String.format("#%d", i), vW - (int) (140 * resScale), currentY);

                lineCount++;
            }

            g2.setFont(fontLogIndex);
            g2.setColor(COLOR_GRAY_TEXT);
            g2.drawString(String.format("Lines: %d/%d", endIndex, totalLogs), vW - (int) (110 * resScale), (int) (22 * resScale));

            // 프롬프트
            int promptX = (int) (25 * resScale);
            String promptPrefix = "root@" + Core.get().getProjectName().toLowerCase() + ":~$ ";
            String currentInput = text.getInputText();
            String fullPrompt = promptPrefix + currentInput;

            g2.setFont(fontPrompt);
            g2.setColor(COLOR_BORDER);
            RU.drawStringWithCursor(g2, promptPrefix, text, "", promptX, promptY, 3, RU.CursorPosition.TOP);

            FontMetrics fmPrompt = g2.getFontMetrics(fontPrompt);
            int cursorX = promptX + fmPrompt.stringWidth(fullPrompt);
            int cursorY = promptY;

            String cursor = (System.currentTimeMillis() % 1000 > 300) ? "_" : "";
            g2.drawString(cursor, cursorX, cursorY);

            // 자동완성 패널
            List<String> allCandidates = getAllCandidates();
            List<String> suggestions = getCurrentSuggestions();

            int boxPadding = (int) (6 * resScale);
            int itemHeight = (int) (18 * resScale);

            // 좌측 전체 후보
            if (!allCandidates.isEmpty()) {
                int maxWordWidth = 0;
                for (String cand : allCandidates) {
                    maxWordWidth = Math.max(maxWordWidth, fmPrompt.stringWidth(cand));
                }
                int panelWidth = Math.max(maxWordWidth + (boxPadding * 2), (int) (70 * resScale));
                int panelHeight = (allCandidates.size() * itemHeight) + (boxPadding * 2);

                int leftPanelX = cursorX - panelWidth - (int) (8 * resScale);
                int leftPanelY = cursorY + (int) (10 * resScale);

                g2.setColor(COLOR_PANEL_BG);
                g2.fillRect(leftPanelX, leftPanelY, panelWidth, panelHeight);
                g2.setColor(COLOR_PANEL_BORDER);
                g2.drawRect(leftPanelX, leftPanelY, panelWidth, panelHeight);

                g2.setFont(fontSuggestSub);
                g2.setColor(COLOR_CANDIDATES_TEXT);
                for (int idx = 0; idx < allCandidates.size(); idx++) {
                    String candidateWord = allCandidates.get(idx);
                    int textY = leftPanelY + boxPadding + (idx + 1) * itemHeight - (int) (3 * resScale);
                    g2.drawString(candidateWord, leftPanelX + boxPadding, textY);
                }
            }

            // 우측 매칭 추천 목록 (선택된 인덱스 하이라이트)
            if (!suggestions.isEmpty()) {
                if (selectedSuggestionIndex >= suggestions.size() || selectedSuggestionIndex < 0) {
                    selectedSuggestionIndex = 0;
                }

                int maxWordWidth = 0;
                for (String sug : suggestions) {
                    maxWordWidth = Math.max(maxWordWidth, fmPrompt.stringWidth(sug));
                }
                int panelWidth = maxWordWidth + (boxPadding * 2);
                int panelHeight = (suggestions.size() * itemHeight) + (boxPadding * 2);

                int rightPanelX = cursorX + (int) (8 * resScale);
                int rightPanelY = cursorY + (int) (10 * resScale);

                g2.setColor(COLOR_PANEL_BG);
                g2.fillRect(rightPanelX, rightPanelY, panelWidth, panelHeight);
                g2.setColor(COLOR_PANEL_BORDER);
                g2.drawRect(rightPanelX, rightPanelY, panelWidth, panelHeight);

                for (int idx = 0; idx < suggestions.size(); idx++) {
                    String suggestionWord = suggestions.get(idx);
                    int textY = rightPanelY + boxPadding + (idx + 1) * itemHeight - (int) (3 * resScale);

                    if (idx == selectedSuggestionIndex) {
                        g2.setFont(fontSuggestTop);
                        g2.setColor(COLOR_LOG_ROOT);
                    } else {
                        g2.setFont(fontSuggestSub);
                        g2.setColor(COLOR_GRAY_TEXT);
                    }
                    g2.drawString(suggestionWord, rightPanelX + boxPadding, textY);
                }
            }

            // 경고 메시지
            FontMetrics fmWarn = g2.getFontMetrics(fontWarning);
            int warnBoxHeight = (int) (20 * resScale);
            int warnBoxY = cursorY - (int) (26 * resScale);

            if (hasConsecutiveSpaces(currentInput)) {
                String msg = "Consecutive spaces detected";
                int warnWidth = fmWarn.stringWidth(msg) + (int) (10 * resScale);
                int warnX = cursorX + (int) (10 * resScale);

                if (warnX + warnWidth > vW - 20) {
                    warnX = vW - 20 - warnWidth;
                }

                g2.setColor(COLOR_WARN_BG);
                g2.fillRect(warnX, warnBoxY, warnWidth, warnBoxHeight);
                g2.setColor(COLOR_WARN_BORDER);
                g2.drawRect(warnX, warnBoxY, warnWidth, warnBoxHeight);

                g2.setColor(COLOR_WARN_TEXT);
                g2.drawString(msg, warnX + 5, warnBoxY + (int) (14 * resScale));

                warnBoxY -= (warnBoxHeight + 4);
            }

            if (hasSyntaxError(currentInput)) {
                String msg = "Unclosed quotes (\")";
                int warnWidth = fmWarn.stringWidth(msg) + (int) (10 * resScale);
                int warnX = cursorX + (int) (10 * resScale);

                if (warnX + warnWidth > vW - 20) {
                    warnX = vW - 20 - warnWidth;
                }

                g2.setColor(COLOR_WARN_BG);
                g2.fillRect(warnX, warnBoxY, warnWidth, warnBoxHeight);
                g2.setColor(COLOR_WARN_BORDER);
                g2.drawRect(warnX, warnBoxY, warnWidth, warnBoxHeight);

                g2.setColor(COLOR_WARN_TEXT);
                g2.drawString(msg, warnX + 5, warnBoxY + (int) (14 * resScale));
            }
        }

        // 시안색 워크타임라인 디버그 텍스트 (isOpen이거나 showDebugScreen이 켜진 경우 항상 최종 렌더링)
        g.setColor(Color.CYAN);
        g.setFont(fontWarning);
        g.drawString(String.format(
                "FPS: %d | frame: %.2f ms | work: %.2f ms | " +
                        "scale: %.6f / requested: %.6f | physical: %.3f (%s%s)",
                base.getFps(),
                base.getFrameTimeMs(),
                base.getRenderWorkTimeMs(),
                base.getViewScale(),
                base.getRequestedViewScale(),
                base.getPhysicalViewScale(),
                base.isFractionalPhysicalScale() ? "fractional" : "integer",
                base.isViewScaleSnapped() ? ", snapped" : ""
        ), 5, (int) (14 * resScale));
    }

    private static final int CONSOLE_KEY_CODE = KeyEvent.VK_BACK_QUOTE;
    private final Canvas targetCanvas;

    private void initBinding() {
        targetCanvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!enable) { return; }

                if (e.getKeyCode() == CONSOLE_KEY_CODE) {
                    toggle();
                    e.consume();
                    return;
                }

                if (isOpen) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_PAGE_UP -> {
                            scrollUp(2);
                            e.consume();
                        }
                        case KeyEvent.VK_PAGE_DOWN -> {
                            scrollDown(2);
                            e.consume();
                        }
                        case KeyEvent.VK_UP -> {
                            List<String> suggestions = getCurrentSuggestions();
                            if (!suggestions.isEmpty()) {
                                selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.size()) % suggestions.size();
                                e.consume();
                            }
                        }
                        case KeyEvent.VK_DOWN -> {
                            List<String> suggestions = getCurrentSuggestions();
                            if (!suggestions.isEmpty()) {
                                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                                e.consume();
                            }
                        }
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (!enable) { return; }
                if (e.getKeyCode() == CONSOLE_KEY_CODE) {
                    e.consume();
                }
            }
        });
    }

    private Boolean parseBoolValue(String val) {
        if ("true".equalsIgnoreCase(val) || "1".equals(val)) return true;
        if ("false".equalsIgnoreCase(val) || "0".equals(val)) return false;
        return null;
    }

    public void internalCMD(List<String> args) {
        if (args == null || args.size() < 2 || !"sys".equals(args.get(0))) {
            return;
        }

        switch (args.get(1)) {
            case "debug" -> {
                if (args.size() < 4) {
                    addLog(LogType.ERROR, "usage: sys debug <bool|1|0> screen");
                    return;
                }

                String valStr;
                if ("screen".equalsIgnoreCase(args.get(3))) {
                    valStr = args.get(2);
                } else if ("screen".equalsIgnoreCase(args.get(2))) {
                    valStr = args.get(3);
                } else {
                    addLog(LogType.ERROR, "target must be 'screen'!");
                    return;
                }

                Boolean targetState = parseBoolValue(valStr);
                if (targetState == null) {
                    addLog(LogType.ERROR, "invalid boolean! use true, false, 1, or 0.");
                    return;
                }

                this.showDebugScreen = targetState;
                addLog(LogType.SYSTEM, "Debug screen set to: " + showDebugScreen);
                text.clear();
            }
            case "up" -> {
                if (args.size() < 3) {
                    scrollUp(1);
                } else {
                    int value;
                    try {
                        value = Integer.parseInt(args.get(2));
                    } catch (NumberFormatException e) {
                        addLog(LogType.ERROR, "value is not a number!");
                        return;
                    }
                    scrollUp(value);
                }
            }
            case "down" -> {
                if (args.size() < 3) {
                    scrollDown(1);
                } else {
                    int value;
                    try {
                        value = Integer.parseInt(args.get(2));
                    } catch (NumberFormatException e) {
                        addLog(LogType.ERROR, "value is not a number!");
                        return;
                    }
                    scrollDown(value);
                }
            }
            case "drct" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR,"line is null!");
                    return;
                }
                int value;
                try {
                    value = Integer.parseInt(args.get(2));
                } catch (NumberFormatException e) {
                    addLog(LogType.ERROR, "value is not a number!");
                    return;
                }

                if (value < 0 || value >= logs.size()) {
                    addLog(LogType.ERROR, "out of line range!");
                    return;
                }

                int targetOffset = logs.size() - value;
                int maxOffset = Math.max(0, logs.size() - maxLines);

                scrollOffset = Math.min(targetOffset, maxOffset);
                text.clear();
            }
            case "copy" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR,"line is null!");
                    return;
                }
                int value;
                try {
                    value = Integer.parseInt(args.get(2));
                } catch (NumberFormatException e) {
                    addLog(LogType.ERROR, "value is not a number!");
                    return;
                }
                if (value < 0 || value >= logs.size()) {
                    addLog(LogType.ERROR, "out of line range!");
                    return;
                }
                text.setInputText(logs.get(value).substring(17));
            }
            case "gc" -> {
                addLog(LogType.SYSTEM,"It's legacy functions");
                text.clear();
            }
            case "getInfo" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR,"fuc is null!");
                    return;
                }
                switch (args.get(2)) {
                    case "ver" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"object is null!");
                            return;
                        }
                        switch (args.get(3)) {
                            case "engine" -> {
                                addLog(LogType.SYSTEM, Base.version);
                                text.clear();
                            }
                            case "sound" -> {
                                addLog(LogType.SYSTEM, "BeisiqTinySound: " + InternalSoundModule.VERSION);
                                text.clear();
                            }
                            case "built" -> {
                                if (args.size() < 5) {
                                    addLog(LogType.ERROR,"object is null!");
                                    return;
                                }
                                switch (args.get(4)) {
                                    case "jdk" -> {
                                        addLog(LogType.SYSTEM, "open jdk 21");
                                        text.clear();
                                    }
                                    case "vm" -> {
                                        addLog(LogType.SYSTEM, "graalvm 21.0.7");
                                        text.clear();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case "quickput" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR,"3rd token is null!");
                    return;
                }
                switch (args.get(2)) {
                    case "put" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"key is null!");
                            return;
                        }
                        if (args.size() < 5) {
                            addLog(LogType.ERROR,"cmd is null!");
                            return;
                        }
                        if (args.size() > 5) {
                            addLog(LogType.ERROR,"should be using \"\" !");
                            return;
                        }
                        getQuickPutManager().map.put(args.get(3),args.get(4));
                        text.clear();
                    }
                    case "delete" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"key is null!");
                            return;
                        }
                        getQuickPutManager().map.remove(args.get(3));
                        text.clear();
                    }
                    case "clear" -> {
                        getQuickPutManager().map.clear();
                        text.clear();
                    }
                }
            }
            case "exe" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR,"object is null!");
                    return;
                }
                switch (args.get(2)) {
                    case "performanceReader" -> {
                        new PerformanceReader().launch();
                    }
                    case "consoleHelper" -> {
                        //new Help();
                    }
                    case "testInConsole" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"object is null!");
                            return;
                        }
                        String message = "test";

                        switch (args.get(3)) {
                            case "errorMessage" -> {
                                addLog(LogType.ERROR,message);
                            }
                            case "consoleMessage" -> {
                                addLog(LogType.CONSOLE,message);
                            }
                            case "systemMessage" -> {
                                addLog(LogType.SYSTEM,message);
                            }
                            case "safe_runtimeMessage" -> {
                                addLog(LogType.SAFE_RUNTIME,message);
                            }
                            default -> {

                            }
                        }
                    }
                    default -> {

                    }
                }
                text.clear();
            }
            default -> {
                List<Map.Entry<String, String>> entryList = new ArrayList<>(getQuickPutManager().map.entrySet());
                for (int i = 0; i < entryList.size(); i++) {
                    Map.Entry<String, String> entry = entryList.get(i);
                    String token = args.get(1);
                    if (token.equals(entry.getKey())) {
                        text.setInputText(entry.getValue());
                    }
                }
            }
        }
    }

    @Internal
    public class QuickPutManager implements IoInterface {
        private Map<String, String> map = new ConcurrentHashMap<>();

        @Override
        public void save(Properties p) {
            List<Map.Entry<String, String>> entryList = new ArrayList<>(map.entrySet());

            p.setProperty("count", String.valueOf(entryList.size()));

            for (int i = 0; i < entryList.size(); i++) {
                Map.Entry<String, String> entry = entryList.get(i);
                String prefix = "q" + i + "_";

                p.setProperty(prefix + "key", entry.getKey());
                p.setProperty(prefix + "cmd", entry.getValue());
            }
        }

        @Override
        public void load(Properties p) {
            int count = Integer.parseInt(p.getProperty("count", "0"));
            Map<String, String> loadedMap = new ConcurrentHashMap<>();

            for (int i = 0; i < count; i++) {
                String prefix = "q" + i + "_";
                String key = p.getProperty(prefix + "key");
                String cmd = p.getProperty(prefix + "cmd");

                if (key != null && cmd != null) {
                    loadedMap.put(key, cmd);
                }
            }

            this.map = loadedMap;
        }

        @Override public void initLoad(Properties p) { }
    }

    public void handleTabCompletion() {
        String inputText = text.getInputText();
        List<String> currentTokens = parseBuffer(inputText, true);

        if (currentTokens.isEmpty()) return;

        String currentToken = currentTokens.get(currentTokens.size() - 1);
        if (currentToken.isEmpty()) return;

        List<String> candidates = autoCompleteManager.getCandidates(currentTokens, currentToken);

        if (!candidates.isEmpty()) {
            if (selectedSuggestionIndex >= candidates.size() || selectedSuggestionIndex < 0) {
                selectedSuggestionIndex = 0;
            }

            String chosenCandidate = candidates.get(selectedSuggestionIndex);
            currentTokens.set(currentTokens.size() - 1, chosenCandidate);
            String completedInput = String.join(" ", currentTokens) + " ";

            text.setInputText(completedInput);
            selectedSuggestionIndex = 0;
        }
    }

    public List<String> getCurrentSuggestions() {
        String inputText = text.getInputText();
        List<String> currentTokens = parseBuffer(inputText, true);
        if (currentTokens.isEmpty()) {
            return Collections.emptyList();
        }
        String currentToken = currentTokens.get(currentTokens.size() - 1);
        return autoCompleteManager.getCandidates(currentTokens, currentToken);
    }

    public List<String> getAllCandidates() {
        String inputText = text.getInputText();
        List<String> currentTokens = parseBuffer(inputText, true);
        if (currentTokens.isEmpty()) {
            return Collections.emptyList();
        }
        return autoCompleteManager.getAllCandidates(currentTokens);
    }
}