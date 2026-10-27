package com.fw.main.utils.platform.system.console;

import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.*;
import com.fw.main.api.io.IoInterface;
import com.fw.main.utils.graphics.RU;
import com.fw.main.utils.input.korean.TextObject;
import com.fw.main.utils.input.korean.TextObjectEventListener;
import com.fw.main.utils.platform.system.asset.Sound;
import com.fw.main.utils.platform.system.asset.Texture;
import com.fw.main.utils.platform.system.asset.internal.sound.kuusisto.tinysound.internal.InternalSoundModule;
import com.fw.main.utils.platform.system.console.autoComplete.AutoCompleteManager;
import com.fw.main.utils.platform.system.console.autoComplete.ConsoleLabel;
import com.fw.main.utils.platform.system.scene.Atlas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.RenderedImage;
import java.io.File;
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
         * It's use only internal option.
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

    private int selectedSuggestionIndex = 0;
    private boolean showDebugScreen = false;

    // 명령어 히스토리 관리
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

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
        rebuildAutoCompleteTree();
    }

    public void rebuildAutoCompleteTree() {
        autoCompleteManager = new AutoCompleteManager();
        autoCompleteManager.tree(root -> {
            root.then("sys", sys -> {
                sys.then("copy").then("10");
                sys.then("drct").then("10");
                sys.then("up");
                sys.then("down");
                sys.then("gc");

                sys.then("scene", scn -> {
                    scn.then(base.getSceneKeyLabels());
                });

                sys.then("quickput", qp -> {
                    qp.then("clear");
                    qp.then("delete").then("key");
                    qp.then("put").then("key").then("CMD");
                });

                sys.then("getInfo", info -> {
                    info.then("ver", ver -> {
                        ver.then("engine");
                        ver.then("sound");
                        ver.then("built", built -> {
                            built.then("jdk");
                            built.then("vm");
                        });
                    });
                });

                sys.then("exe", exe -> {
                    exe.then("performanceReader");
                    exe.then("settingEditor");
                    exe.then("testInConsole", test -> {
                        test.then("errorMessage");
                        test.then("consoleMessage");
                        test.then("systemMessage");
                        test.then("safe_runtimeMessage");
                    });
                });

                sys.then("debug", debug -> {
                    debug.then(target -> {
                        target.then("screen");
                        target.then("hitbox");
                        target.then("atlas");
                    }, "true", "false");

                    debug.then("asset", asset -> {
                        asset.then("texture", tex -> {
                            tex.then(base.assetManager.getTextureKeyLabels(), loc -> {
                                loc.then("desktop");
                                loc.then("projectFolder");
                                loc.then("exeFolder");
                            });
                        });
                        asset.then("sound", snd -> {
                            snd.then(base.assetManager.getSoundKeyLabels());
                        });
                        asset.then("music", msc -> {
                            msc.then(base.assetManager.getMusicKeyLabels(), state -> {
                                state.then("true");
                                state.then("false");
                            });
                        });
                        asset.then("atlas", atl -> {
                            atl.then(base.assetManager.getAtlasKeyLabels(), loc -> {
                                loc.then("desktop");
                                loc.then("projectFolder");
                                loc.then("exeFolder");
                            });
                        });
                    });
                });

                sys.then(getQuickPutManager().getKeyLabels());

                sys.then("dump");
                sys.then("clear");
                sys.then("mem");
                sys.then("thread");
            });
        });
    }

    public boolean isOpen() { return isOpen; }
    public void toggle() {
        isOpen = !isOpen;
        text.setFocused(!text.isFocused());
        text.clear();
        selectedSuggestionIndex = 0;
        historyIndex = -1;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
    }

    public void scrollUp(int lines) {
        synchronized (logs) {
            int maxOffset = Math.max(0, logs.size() - maxLines);
            scrollOffset = Math.min(scrollOffset + lines, maxOffset);
        }
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

        // 명령어 히스토리 추가 (직전 명령어가 동일한 경우 중복 방지)
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(input)) {
            commandHistory.add(input);
        }
        historyIndex = -1;

        if (args.get(0) != null && args.get(0).equals("sys")) {
            internalCMD(args);
            return;
        }

        synchronized (logs) {
            logs.add(String.format("[%tT] %s%s", System.currentTimeMillis(), LogType.ROOT.get(), input));
            scrollOffset = 0;
        }
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
        if (message == null) return;

        if (message.contains("\n") || message.contains("\r")) {
            String[] splitLines = message.split("\\r?\\n");
            for (String line : splitLines) {
                if (!line.isEmpty()) {
                    addLog(type, line);
                }
            }
            return;
        }

        String logEntry = String.format("[%tT] %s%s", System.currentTimeMillis(), type.get(), message);

        synchronized (logs) {
            logs.add(logEntry);
            scrollOffset = 0;
        }
    }

    public void exportLogsToTxt(java.io.File destination) {
        if (destination == null) return;
        try {
            java.io.File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(destination), java.nio.charset.StandardCharsets.UTF_8))) {

                synchronized (logs) {
                    for (String logLine : logs) {
                        writer.write(logLine);
                        writer.newLine();
                    }
                }
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("[Console] Failed to dump console logs: " + e.getMessage());
        }
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

            // 렌더링 시 logs 동기화 스냅샷 순회
            synchronized (logs) {
                int totalLogs = logs.size();
                int endIndex = Math.max(0, totalLogs - scrollOffset);
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
            }

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

            // 우측 매칭 추천 목록
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

        // 시안색 워크타임라인 디버그 텍스트
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
                            } else if (!commandHistory.isEmpty()) {
                                if (historyIndex == -1) {
                                    historyIndex = commandHistory.size() - 1;
                                } else if (historyIndex > 0) {
                                    historyIndex--;
                                }
                                text.setInputText(commandHistory.get(historyIndex));
                            }
                            e.consume();
                        }
                        case KeyEvent.VK_DOWN -> {
                            List<String> suggestions = getCurrentSuggestions();
                            if (!suggestions.isEmpty()) {
                                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                            } else if (!commandHistory.isEmpty() && historyIndex != -1) {
                                if (historyIndex < commandHistory.size() - 1) {
                                    historyIndex++;
                                    text.setInputText(commandHistory.get(historyIndex));
                                } else {
                                    historyIndex = -1;
                                    text.clear();
                                }
                            }
                            e.consume();
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
            case "scene" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR, "usage: sys scene <sceneKey>");
                    return;
                }
                String targetScene = args.get(2);
                boolean success = base.changeScene(targetScene);
                if (success) {
                    addLog(LogType.SYSTEM, "Transitioning to scene: " + targetScene);
                } else {
                    addLog(LogType.ERROR, "Failed to change scene (invalid key or already transitioning): " + targetScene);
                }
                text.clear();
            }
            case "debug" -> {
                if (args.size() < 3) {
                    addLog(LogType.ERROR, "usage: sys debug <bool|1|0> <target> OR sys debug asset <texture|sound|music|atlas> ...");
                    return;
                }

                if ("asset".equalsIgnoreCase(args.get(2))) {
                    if (args.size() < 5) {
                        addLog(LogType.ERROR, "usage: sys debug asset <texture|sound|music|atlas> <key> [desktop|projectFolder|exeFolder]");
                        return;
                    }

                    String subType = args.get(3).toLowerCase();
                    String assetKey = args.get(4);

                    switch (subType) {
                        case "texture" -> {
                            if (args.size() < 6) {
                                addLog(LogType.ERROR, "usage: sys debug asset texture <key> <desktop|projectFolder|exeFolder>");
                                return;
                            }
                            String targetLoc = args.get(5).toLowerCase();
                            Texture tex = base.assetManager.getTexture(assetKey);
                            if (tex == null || tex.getVolatileImage() == null) {
                                addLog(LogType.ERROR, "texture not found: " + assetKey);
                                return;
                            }

                            File targetDir = switch (targetLoc) {
                                case "desktop" -> new File(System.getProperty("user.home"), "Desktop");
                                case "projectfolder" -> new File(InternalUtils.getProjectFolder());
                                case "exefolder" -> new File(System.getProperty("user.dir"));
                                default -> null;
                            };

                            if (targetDir == null) {
                                addLog(LogType.ERROR, "unknown export location: " + targetLoc);
                                return;
                            }
                            if (!targetDir.exists()) targetDir.mkdirs();

                            String cleanFileName = assetKey.replaceAll("[^a-zA-Z0-9._-]", "_");
                            if (!cleanFileName.toLowerCase().endsWith(".png")) cleanFileName += ".png";
                            File outputFile = new File(targetDir, cleanFileName);

                            try {
                                ImageIO.write(tex.getBufferedImage(), "png", outputFile);
                                addLog(LogType.SYSTEM, "Texture dumped to: " + outputFile.getAbsolutePath());
                            } catch (Exception e) {
                                addLog(LogType.ERROR, "failed to export texture: " + e.getMessage());
                            }
                        }
                        case "sound" -> {
                            Sound sound = base.assetManager.getSound(assetKey);
                            if (sound == null) {
                                addLog(LogType.ERROR, "sound not found: " + assetKey);
                                return;
                            }
                            sound.play();
                            addLog(LogType.SYSTEM, "Played sound: " + assetKey);
                        }
                        case "music" -> {
                            boolean play = true;
                            if (args.size() >= 6) {
                                Boolean parsed = parseBoolValue(args.get(5));
                                if (parsed == null) {
                                    addLog(LogType.ERROR, "invalid boolean! use true, false, 1, or 0.");
                                    return;
                                }
                                play = parsed;
                            }

                            com.fw.main.utils.platform.system.asset.Music music = base.assetManager.getMusic(assetKey);
                            if (music == null) {
                                addLog(LogType.ERROR, "music not found: " + assetKey);
                                return;
                            }

                            if (play) {
                                music.play(true);
                                addLog(LogType.SYSTEM, "Playing music (loop): " + assetKey);
                            } else {
                                music.stop();
                                addLog(LogType.SYSTEM, "Stopped music: " + assetKey);
                            }
                        }
                        case "atlas" -> {
                            if (args.size() < 6) {
                                addLog(LogType.ERROR, "usage: sys debug asset atlas <key> <desktop|projectFolder|exeFolder>");
                                return;
                            }
                            String targetLoc = args.get(5).toLowerCase();
                            Atlas atlas = base.assetManager.getAtlas(assetKey);
                            if (atlas == null || atlas.getVolatileImage() == null || atlas.getVolatileImage() == null) {
                                addLog(LogType.ERROR, "atlas not found or not baked: " + assetKey);
                                return;
                            }

                            File targetDir = switch (targetLoc) {
                                case "desktop" -> new File(System.getProperty("user.home"), "Desktop");
                                case "projectfolder" -> new File(InternalUtils.getProjectFolder());
                                case "exefolder" -> new File(System.getProperty("user.dir"));
                                default -> null;
                            };

                            if (targetDir == null) {
                                addLog(LogType.ERROR, "unknown export location: " + targetLoc);
                                return;
                            }
                            if (!targetDir.exists()) targetDir.mkdirs();

                            String cleanFileName = "atlas_" + assetKey.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
                            File outputFile = new File(targetDir, cleanFileName);

                            try {
                                ImageIO.write(atlas.getVolatileImage().getSnapshot(), "png", outputFile);
                                addLog(LogType.SYSTEM, "Atlas texture dumped to: " + outputFile.getAbsolutePath());
                            } catch (Exception e) {
                                addLog(LogType.ERROR, "failed to export atlas texture: " + e.getMessage());
                            }
                        }
                        default -> addLog(LogType.ERROR, "unknown asset type: " + subType + " (use texture, sound, music, atlas)");
                    }
                    text.clear();
                    return;
                }
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

                synchronized (logs) {
                    if (value < 0 || value >= logs.size()) {
                        addLog(LogType.ERROR, "out of line range!");
                        return;
                    }

                    int targetOffset = logs.size() - value;
                    int maxOffset = Math.max(0, logs.size() - maxLines);
                    scrollOffset = Math.min(targetOffset, maxOffset);
                }
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
                synchronized (logs) {
                    if (value < 0 || value >= logs.size()) {
                        addLog(LogType.ERROR, "out of line range!");
                        return;
                    }
                    text.setInputText(logs.get(value).substring(17));
                }
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
                                addLog(LogType.SYSTEM, "TinySound: " + InternalSoundModule.VERSION);
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
                        getQuickPutManager().map.put(args.get(3), args.get(4));
                        rebuildAutoCompleteTree();
                        text.clear();
                    }
                    case "delete" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"key is null!");
                            return;
                        }
                        getQuickPutManager().map.remove(args.get(3));
                        rebuildAutoCompleteTree();
                        text.clear();
                    }
                    case "clear" -> {
                        getQuickPutManager().map.clear();
                        rebuildAutoCompleteTree();
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
                    case "settingEditor" -> {
                        new EngineSettingEditor(Core.get().getProjectName()).setVisible(true);
                    }
                    case "testInConsole" -> {
                        if (args.size() < 4) {
                            addLog(LogType.ERROR,"object is null!");
                            return;
                        }
                        String message = "test";

                        switch (args.get(3)) {
                            case "errorMessage" -> addLog(LogType.ERROR, message);
                            case "consoleMessage" -> addLog(LogType.CONSOLE, message);
                            case "systemMessage" -> addLog(LogType.SYSTEM, message);
                            case "safe_runtimeMessage" -> addLog(LogType.SAFE_RUNTIME, message);
                        }
                    }
                }
                text.clear();
            }
            case "dump" -> {
                File dumpFile;
                if (args.size() >= 3) {
                    dumpFile = new java.io.File(args.get(2));
                } else {
                    String userHome = System.getProperty("user.home");
                    String projName = (Core.get() != null && Core.get().getProjectName() != null) ? Core.get().getProjectName() : "app";
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    dumpFile = new java.io.File(userHome + java.io.File.separator + "." + projName
                            + java.io.File.separator + "logs" + java.io.File.separator + "console_" + timestamp + ".txt");
                }
                exportLogsToTxt(dumpFile);
                addLog(LogType.SYSTEM, "Logs dumped to: " + dumpFile.getAbsolutePath());
                text.clear();
            }
            case "clear" -> {
                synchronized (logs) {
                    logs.clear();
                }
                scrollOffset = 0;
                text.clear();
            }
            case "mem" -> {
                Runtime rt = Runtime.getRuntime();
                long total = rt.totalMemory() / (1024 * 1024);
                long free = rt.freeMemory() / (1024 * 1024);
                long used = total - free;
                long max = rt.maxMemory() / (1024 * 1024);

                addLog(LogType.SYSTEM, String.format("Heap: %dMB / %dMB (Used: %dMB, Max: %dMB)", total, max, used, max));
                text.clear();
            }
            case "thread" -> {
                ThreadGroup tg = Thread.currentThread().getThreadGroup();
                while (tg.getParent() != null) {
                    tg = tg.getParent();
                }

                Thread[] active = new Thread[tg.activeCount()];
                int count = tg.enumerate(active);

                addLog(LogType.SYSTEM, "Active Threads: " + count);
                for (int i = 0; i < count; i++) {
                    Thread t = active[i];
                    if (t != null && !t.isDaemon()) {
                        addLog(LogType.CONSOLE, String.format(" - [%d] %s (%s)", t.getId(), t.getName(), t.getState()));
                    }
                }
                text.clear();
            }
            default -> {
                List<Map.Entry<String, String>> entryList = new ArrayList<>(getQuickPutManager().map.entrySet());
                for (Map.Entry<String, String> entry : entryList) {
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

        private record QuickPutKeyLabel(String key) implements ConsoleLabel {
            @Override
            public String getConsoleLabel() {
                return key;
            }
        }

        private final List<ConsoleLabel> keyLabelsView = new AbstractList<ConsoleLabel>() {
            @Override
            public ConsoleLabel get(int index) {
                int i = 0;
                for (String key : map.keySet()) {
                    if (i++ == index) return new QuickPutKeyLabel(key);
                }
                throw new IndexOutOfBoundsException(index);
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public Iterator<ConsoleLabel> iterator() {
                Iterator<String> keyIterator = map.keySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keyIterator.hasNext();
                    }

                    @Override
                    public ConsoleLabel next() {
                        return new QuickPutKeyLabel(keyIterator.next());
                    }
                };
            }
        };

        public List<ConsoleLabel> getKeyLabels() {
            return keyLabelsView;
        }

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
            rebuildAutoCompleteTree(); // 저장 데이터 로드 후 트리 갱신
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