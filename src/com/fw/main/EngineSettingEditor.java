package com.fw.main;

import com.fw.main.utils.graphics.RenderingOption;
import com.fw.main.utils.platform.system.performance.PerformanceRecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EngineSettingEditor extends JFrame {

    private final String projectName;
    private final Base baseInstance;
    private final Map<String, JComponent> inputFields = new LinkedHashMap<>();
    private final List<String> missingKeys = new ArrayList<>();

    public EngineSettingEditor(Base base) {
        this(base, resolveProjectName(base));
    }

    public EngineSettingEditor() {
        this(null, resolveProjectName(null));
    }

    public EngineSettingEditor(String projectName) {
        this(null, projectName);
    }

    private EngineSettingEditor(Base base, String projectName) {
        super("Engine Setting Editor - [" + projectName + "]");
        this.baseInstance = base;
        this.projectName = projectName;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(580, 750);
        setLocationRelativeTo(null);

        initUI();

        if (!missingKeys.isEmpty()) {
            SwingUtilities.invokeLater(this::showMissingSettingsWarning);
        }
    }

    private static String resolveProjectName(Base base) {
        if (Core.isIsSetConfig()) {
            return Core.get().getSettingsProjectName();
        }
        return "OlenProject";
    }

    private void showMissingSettingsWarning() {
        String message = String.format(
                "Some settings (%d) are missing from the configuration directory (%s).\n" +
                        "The currently displayed values are fallbacks and may cause the engine to run differently.\n\n" +
                        "Missing keys: %s",
                missingKeys.size(),
                EngineSettings.directory(projectName).toAbsolutePath(),
                String.join(", ", missingKeys.subList(0, Math.min(3, missingKeys.size()))) + (missingKeys.size() > 3 ? "..." : "")
        );
        JOptionPane.showMessageDialog(this, message, "Missing Settings Warning", JOptionPane.WARNING_MESSAGE);
    }

    private String readConfig(String key, String fallback) {
        Path settingFile = EngineSettings.file(projectName, key);
        if (!Files.isRegularFile(settingFile)) {
            if (!key.equals("base.dumpPerformanceDataFileName") && !missingKeys.contains(key)) {
                missingKeys.add(key);
            }
            return fallback;
        }

        return EngineSettings.read(projectName, key, fallback);
    }

    private boolean readBool(String key, boolean fallback) {
        String val = readConfig(key, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(val) ? true : "false".equalsIgnoreCase(val) ? false : fallback;
    }

    private int readInt(String key, int fallback) {
        try {
            return Integer.parseInt(readConfig(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E readEnum(String key, Class<E> enumClass, E fallback) {
        String val = readConfig(key, fallback.name());
        try {
            return Enum.valueOf(enumClass, val);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void initUI() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        int row = 0;

        row = addField(formPanel, gbc, row, "base.consoleUse",
                createCheckBox("Console Use", readBool("base.consoleUse", false)));
        row = addField(formPanel, gbc, row, "base.closeWindowWithKillVM",
                createCheckBox("Kill VM on Window Close", readBool("base.closeWindowWithKillVM", true)));
        row = addField(formPanel, gbc, row, "base.crtJitterEnabled",
                createCheckBox("CRT Jitter", readBool("base.crtJitterEnabled", true)));
        row = addField(formPanel, gbc, row, "base.fullScreen",
                createCheckBox("Full Screen", readBool("base.fullScreen", false)));
        row = addField(formPanel, gbc, row, "base.changeResolution",
                createCheckBox("Change Display Resolution", readBool("base.changeResolution", false)));
        row = addField(formPanel, gbc, row, "base.useEngineCursor",
                createCheckBox("Use Custom Engine Cursor", readBool("base.useEngineCursor", false)));

        row = addField(formPanel, gbc, row, "base.renderingOption",
                createComboBox(RenderingOption.values(), readEnum("base.renderingOption", RenderingOption.class, RenderingOption.DEFAULT)));
        row = addField(formPanel, gbc, row, "base.waitMode",
                createComboBox(Base.WaitMode.values(), readEnum("base.waitMode", Base.WaitMode.class, Base.WaitMode.OS_SLEEP)));
        row = addField(formPanel, gbc, row, "base.threadMode",
                createComboBox(Base.ThreadMode.values(), readEnum("base.threadMode", Base.ThreadMode.class, Base.ThreadMode.SINGLE)));
        row = addField(formPanel, gbc, row, "base.drawMode",
                createComboBox(Base.DrawMode.values(), readEnum("base.drawMode", Base.DrawMode.class, Base.DrawMode.DIRECT)));
        row = addField(formPanel, gbc, row, "base.performanceRecorderOption",
                createComboBox(PerformanceRecorder.CaptureMode.values(), readEnum("base.performanceRecorderOption", PerformanceRecorder.CaptureMode.class, PerformanceRecorder.CaptureMode.DO_NOT)));

        row = addField(formPanel, gbc, row, "base.virtualScreenWidth",
                createSpinner(readInt("base.virtualScreenWidth", 1920), 320, 7680));
        row = addField(formPanel, gbc, row, "base.virtualScreenHeight",
                createSpinner(readInt("base.virtualScreenHeight", 1080), 240, 4320));
        row = addField(formPanel, gbc, row, "base.bufferWidth",
                createSpinner(readInt("base.bufferWidth", 1920), 320, 7680));
        row = addField(formPanel, gbc, row, "base.bufferHeight",
                createSpinner(readInt("base.bufferHeight", 1080), 240, 4320));
        row = addField(formPanel, gbc, row, "base.targetResWidth",
                createSpinner(readInt("base.targetResWidth", -1), -1, 7680));
        row = addField(formPanel, gbc, row, "base.targetResHeight",
                createSpinner(readInt("base.targetResHeight", -1), -1, 4320));
        row = addField(formPanel, gbc, row, "base.targetBitDepth",
                createSpinner(readInt("base.targetBitDepth", DisplayMode.BIT_DEPTH_MULTI), -1, 64));
        row = addField(formPanel, gbc, row, "base.targetRefreshRate",
                createSpinner(readInt("base.targetRefreshRate", DisplayMode.REFRESH_RATE_UNKNOWN), -1, 360));

        row = addField(formPanel, gbc, row, "base.dumpPerformanceDataFileName",
                new JTextField(readConfig("base.dumpPerformanceDataFileName", "")));

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JButton btnSave = new JButton("Save");
        JButton btnReload = new JButton("Reload");
        JButton btnPurge = new JButton("Purge Settings Directory (Reset & Exit)");
        btnPurge.setForeground(new Color(220, 30, 30));

        btnSave.addActionListener(e -> saveAll());
        btnReload.addActionListener(e -> reloadFromDisk());
        btnPurge.addActionListener(e -> purgeSettingsAndExit());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(btnReload);
        buttonPanel.add(btnSave);
        buttonPanel.add(btnPurge);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private JCheckBox createCheckBox(String label, boolean initialVal) {
        JCheckBox cb = new JCheckBox(label);
        cb.setSelected(initialVal);
        return cb;
    }

    private <T> JComboBox<T> createComboBox(T[] items, T selected) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setSelectedItem(selected);
        return combo;
    }

    private JSpinner createSpinner(int initial, int min, int max) {
        int clamped = Math.max(min, Math.min(max, initial));
        SpinnerNumberModel model = new SpinnerNumberModel(clamped, min, max, 1);
        return new JSpinner(model);
    }

    private int addField(JPanel panel, GridBagConstraints gbc, int row, String key, JComponent input) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        JLabel label = new JLabel(key);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        panel.add(input, gbc);

        inputFields.put(key, input);
        return row + 1;
    }

    private void reloadFromDisk() {
        missingKeys.clear();
        for (Map.Entry<String, JComponent> entry : inputFields.entrySet()) {
            String key = entry.getKey();
            JComponent comp = entry.getValue();

            if (comp instanceof JCheckBox) {
                ((JCheckBox) comp).setSelected(readBool(key, false));
            } else if (comp instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) comp;
                String currentVal = readConfig(key, "");
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (combo.getItemAt(i).toString().equalsIgnoreCase(currentVal)) {
                        combo.setSelectedIndex(i);
                        break;
                    }
                }
            } else if (comp instanceof JSpinner) {
                JSpinner spinner = (JSpinner) comp;
                int val = readInt(key, (Integer) spinner.getValue());
                spinner.setValue(val);
            } else if (comp instanceof JTextField) {
                ((JTextField) comp).setText(readConfig(key, ""));
            }
        }

        if (!missingKeys.isEmpty()) {
            showMissingSettingsWarning();
        }
    }

    private void saveAll() {
        for (Map.Entry<String, JComponent> entry : inputFields.entrySet()) {
            String key = entry.getKey();
            JComponent comp = entry.getValue();
            String val = "";

            if (comp instanceof JCheckBox) {
                val = Boolean.toString(((JCheckBox) comp).isSelected());
            } else if (comp instanceof JComboBox) {
                Object item = ((JComboBox<?>) comp).getSelectedItem();
                val = (item != null) ? item.toString() : "";
            } else if (comp instanceof JSpinner) {
                val = ((JSpinner) comp).getValue().toString();
            } else if (comp instanceof JTextField) {
                val = ((JTextField) comp).getText().trim();
            }

            EngineSettings.write(projectName, key, val);
        }
        missingKeys.clear();
        JOptionPane.showMessageDialog(this, "Settings saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void purgeSettingsAndExit() {
        Path engineSettingsDir = EngineSettings.directory(projectName);
        int result = JOptionPane.showConfirmDialog(
                this,
                "This will permanently delete the EngineSettings directory and exit.\nPath: " + engineSettingsDir.toAbsolutePath(),
                "Purge and Exit Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION) return;

        try {
            EngineSettings.reset(projectName);

            if (Files.exists(engineSettingsDir)) {
                Files.walkFileTree(engineSettingsDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to purge directory: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (baseInstance != null) {
            baseInstance.exit(true);
        } else {
            System.exit(0);
        }
    }
}