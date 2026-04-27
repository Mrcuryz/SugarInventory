package com.Laibin.SugarInventory.printerassistant.desktop;

import com.Laibin.SugarInventory.printerassistant.PrinterAssistantBootstrap;
import com.Laibin.SugarInventory.printerassistant.model.LastPrintStatusView;
import com.Laibin.SugarInventory.printerassistant.model.LocalPrinterConfigView;
import com.Laibin.SugarInventory.printerassistant.model.PrinterAssistantStatusView;
import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantFacadeService;
import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantPaths;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrinterAssistantDesktopFrame extends JFrame {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String[] launchArgs;
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "printer-assistant-desktop");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean trayHintShown = new AtomicBoolean(false);

    private ConfigurableApplicationContext applicationContext;
    private TrayIcon trayIcon;

    private final JLabel serviceStatusValue = createValueLabel("启动中");
    private final JLabel serviceMessageValue = createValueLabel("正在初始化本地打印服务");
    private final JLabel addressValue = createValueLabel("127.0.0.1:9527");
    private final JLabel printerCountValue = createValueLabel("-");
    private final JLabel systemDefaultPrinterValue = createValueLabel("-");
    private final JLabel recentPrintStatusValue = createValueLabel("暂无打印记录");
    private final JComboBox<String> printerComboBox = new JComboBox<>();
    private final JCheckBox launchOnStartupCheckBox = new JCheckBox("开机自动启动打印助手");
    private final JButton refreshButton = new JButton("刷新状态");
    private final JButton saveButton = new JButton("保存默认打印机");
    private final JButton testPrintButton = new JButton("测试打印");
    private final JButton openLogsButton = new JButton("打开日志目录");
    private final JButton viewLogsButton = new JButton("查看日志");
    private final JButton restartServiceButton = new JButton("重启服务");
    private final Timer refreshTimer;

    public PrinterAssistantDesktopFrame(String[] launchArgs) {
        this.launchArgs = launchArgs == null ? new String[0] : launchArgs.clone();
        setTitle("标签打印助手");
        setSize(820, 560);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.CENTER);
        setupActions();
        installTray();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hideToTrayOrExit();
            }
        });
        refreshTimer = new Timer(5000, event -> refreshStatusAsync(false));
        refreshTimer.start();
        startServiceAsync();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("标签打印助手");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        JLabel subtitleLabel = new JLabel("本地常驻服务，供 Web 管理端直接提交标签打印任务。");
        subtitleLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(8));
        headerPanel.add(subtitleLabel);

        JPanel statusPanel = createSectionPanel("服务状态");
        statusPanel.add(createInfoRow("服务状态", serviceStatusValue));
        statusPanel.add(createInfoRow("服务说明", serviceMessageValue));
        statusPanel.add(createInfoRow("本地地址", addressValue));
        statusPanel.add(createInfoRow("最近打印", recentPrintStatusValue));

        JPanel printerPanel = createSectionPanel("打印机配置");
        printerPanel.add(createInfoRow("系统默认打印机", systemDefaultPrinterValue));
        printerPanel.add(createInfoRow("已识别打印机数量", printerCountValue));
        printerPanel.add(createComboRow("默认打印机", printerComboBox));
        printerPanel.add(createCheckboxRow(launchOnStartupCheckBox));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(refreshButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(saveButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(testPrintButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(restartServiceButton);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(openLogsButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(viewLogsButton);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.add(statusPanel, BorderLayout.NORTH);
        centerPanel.add(printerPanel, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        return root;
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JPanel createInfoRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        JLabel labelComponent = new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(120, 28));
        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return row;
    }

    private JPanel createComboRow(String label, JComboBox<String> comboBox) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        JLabel labelComponent = new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(120, 28));
        comboBox.setEditable(false);
        comboBox.setPreferredSize(new Dimension(360, 30));
        row.add(labelComponent, BorderLayout.WEST);
        row.add(comboBox, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return row;
    }

    private JPanel createCheckboxRow(JCheckBox checkBox) {
        JPanel row = new JPanel(new BorderLayout());
        row.add(checkBox, BorderLayout.WEST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return row;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private void setupActions() {
        refreshButton.addActionListener(event -> refreshStatusAsync(true));
        saveButton.addActionListener(event -> saveSettingsAsync());
        testPrintButton.addActionListener(event -> testPrintAsync());
        openLogsButton.addActionListener(event -> openLogsDirectory());
        viewLogsButton.addActionListener(event -> openLogViewerDialog());
        restartServiceButton.addActionListener(event -> restartServiceAsync());
    }

    private void startServiceAsync() {
        setControlsEnabled(false);
        setServiceStatus("启动中", "正在初始化本地打印服务", new Color(0xC97A00));
        workerExecutor.submit(() -> {
            try {
                applicationContext = PrinterAssistantBootstrap.start(launchArgs);
                SwingUtilities.invokeLater(() -> {
                    setControlsEnabled(true);
                    setServiceStatus("运行中", "本地 HTTP 服务已启动，可供 Web 调用", new Color(0x0A7C2F));
                    refreshStatusNow();
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    setControlsEnabled(false);
                    setServiceStatus("异常", exception.getMessage(), new Color(0xC62828));
                    showMessage("打印助手启动失败", exception.getMessage(), JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void restartServiceAsync() {
        setControlsEnabled(false);
        workerExecutor.submit(() -> {
            shutdownContext();
            applicationContext = null;
            SwingUtilities.invokeLater(() -> {
                setServiceStatus("启动中", "正在重新启动本地打印服务", new Color(0xC97A00));
                startServiceAsync();
            });
        });
    }

    private void refreshStatusAsync(boolean notifyOnFailure) {
        workerExecutor.submit(() -> {
            try {
                refreshStatusNow();
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    setServiceStatus("异常", exception.getMessage(), new Color(0xC62828));
                    if (notifyOnFailure) {
                        showMessage("刷新状态失败", exception.getMessage(), JOptionPane.WARNING_MESSAGE);
                    }
                });
            }
        });
    }

    private void refreshStatusNow() {
        PrinterAssistantFacadeService facadeService = requireFacadeService();
        List<String> printers = facadeService.listPrinters();
        LocalPrinterConfigView configView = facadeService.getConfig();
        PrinterAssistantStatusView statusView = facadeService.getStatus(true, "printer-assistant-online");
        boolean autoStartEnabled = facadeService.isLaunchOnStartupEnabled();
        boolean autoStartSupported = facadeService.isLaunchOnStartupSupported();

        SwingUtilities.invokeLater(() -> {
            updatePrinterCombo(printers, configView.defaultPrinterName());
            systemDefaultPrinterValue.setText(defaultText(configView.systemDefaultPrinterName()));
            printerCountValue.setText(String.valueOf(printers.size()));
            recentPrintStatusValue.setText(formatLastPrintStatus(statusView.lastPrintStatus()));
            launchOnStartupCheckBox.setSelected(autoStartEnabled);
            launchOnStartupCheckBox.setEnabled(autoStartSupported);
            launchOnStartupCheckBox.setToolTipText(autoStartSupported ? null : "当前运行方式不支持配置开机自启，请使用安装包版本");
            setServiceStatus("运行中", "本地 HTTP 服务已启动，可供 Web 调用", new Color(0x0A7C2F));
        });
    }

    private void updatePrinterCombo(List<String> printers, String selectedPrinterName) {
        printerComboBox.removeAllItems();
        printerComboBox.addItem("");
        for (String printer : printers) {
            printerComboBox.addItem(printer);
        }
        printerComboBox.setSelectedItem(selectedPrinterName == null ? "" : selectedPrinterName);
    }

    private void saveSettingsAsync() {
        workerExecutor.submit(() -> {
            try {
                PrinterAssistantFacadeService facadeService = requireFacadeService();
                String selectedPrinter = normalizeSelectedPrinter();
                facadeService.saveDefaultPrinter(selectedPrinter);
                facadeService.setLaunchOnStartup(launchOnStartupCheckBox.isSelected());
                refreshStatusNow();
                SwingUtilities.invokeLater(() ->
                        showMessage("保存成功", "默认打印机与开机自启配置已保存。", JOptionPane.INFORMATION_MESSAGE)
                );
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() ->
                        showMessage("保存失败", exception.getMessage(), JOptionPane.ERROR_MESSAGE)
                );
            }
        });
    }

    private void testPrintAsync() {
        workerExecutor.submit(() -> {
            try {
                PrinterAssistantFacadeService facadeService = requireFacadeService();
                facadeService.printTest(normalizeSelectedPrinter(), 1);
                refreshStatusNow();
                SwingUtilities.invokeLater(() ->
                        showMessage("测试打印已提交", "请检查打印机是否收到测试标签任务。", JOptionPane.INFORMATION_MESSAGE)
                );
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() ->
                        showMessage("测试打印失败", exception.getMessage(), JOptionPane.ERROR_MESSAGE)
                );
            }
        });
    }

    private void openLogsDirectory() {
        Path logsDir = PrinterAssistantPaths.getLogsDir();
        try {
            Files.createDirectories(logsDir);
            Desktop.getDesktop().open(logsDir.toFile());
        } catch (IOException exception) {
            showMessage("打开日志目录失败", exception.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openLogViewerDialog() {
        JDialog dialog = new JDialog(this, "最近日志", false);
        JTextArea textArea = new JTextArea(readLogTail());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String readLogTail() {
        Path logFile = PrinterAssistantPaths.getLogFilePath();
        if (!Files.exists(logFile)) {
            return "当前尚未生成日志文件。\n\n日志路径：\n" + logFile;
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int fromIndex = Math.max(lines.size() - 200, 0);
            return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
        } catch (IOException exception) {
            return "读取日志失败： " + exception.getMessage();
        }
    }

    private void installTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        PopupMenu trayMenu = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        MenuItem exitItem = new MenuItem("Exit");
        openItem.addActionListener(event -> SwingUtilities.invokeLater(this::restoreWindow));
        exitItem.addActionListener(event -> shutdownAndExit());
        trayMenu.add(openItem);
        trayMenu.addSeparator();
        trayMenu.add(exitItem);

        trayIcon = new TrayIcon(createTrayImage(), "Label Printer Assistant", trayMenu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> SwingUtilities.invokeLater(this::restoreWindow));
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception ignored) {
            trayIcon = null;
        }
    }

    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0x0E5EA8));
            graphics.fillRoundRect(0, 0, 32, 32, 8, 8);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            graphics.drawString("打", 7, 23);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void hideToTrayOrExit() {
        if (trayIcon == null) {
            shutdownAndExit();
            return;
        }
        setVisible(false);
        if (trayHintShown.compareAndSet(false, true)) {
            trayIcon.displayMessage(
                    "Assistant Running",
                    "The window is minimized to tray. Use the main window for settings and test print.",
                    TrayIcon.MessageType.INFO
            );
        }
    }

    private void restoreWindow() {
        setVisible(true);
        setExtendedState(JFrame.NORMAL);
        toFront();
        requestFocus();
    }

    private void shutdownAndExit() {
        refreshTimer.stop();
        shutdownContext();
        workerExecutor.shutdownNow();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        dispose();
        System.exit(0);
    }

    private void shutdownContext() {
        if (applicationContext != null) {
            try {
                applicationContext.close();
            } catch (Exception ignored) {
            }
        }
    }

    private PrinterAssistantFacadeService requireFacadeService() {
        if (applicationContext == null || !applicationContext.isActive()) {
            throw new IllegalStateException("本地打印服务尚未启动完成");
        }
        return applicationContext.getBean(PrinterAssistantFacadeService.class);
    }

    private void setServiceStatus(String statusText, String messageText, Color statusColor) {
        serviceStatusValue.setText(statusText);
        serviceStatusValue.setForeground(statusColor);
        serviceMessageValue.setText(defaultText(messageText));
    }

    private void setControlsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        testPrintButton.setEnabled(enabled);
        openLogsButton.setEnabled(true);
        viewLogsButton.setEnabled(true);
        restartServiceButton.setEnabled(true);
        printerComboBox.setEnabled(enabled);
        launchOnStartupCheckBox.setEnabled(enabled);
    }

    private String normalizeSelectedPrinter() {
        Object selectedItem = printerComboBox.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        String text = selectedItem.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String formatLastPrintStatus(LastPrintStatusView statusView) {
        if (statusView == null) {
            return "暂无打印记录";
        }
        String timeText = statusView.timestamp() == null ? "-" : statusView.timestamp().format(TIME_FORMATTER);
        String stateText = statusView.success() ? "成功" : "失败";
        String printerText = defaultText(statusView.printerName());
        return String.format("%s | %s | 打印机：%s | %s",
                timeText,
                stateText,
                printerText,
                defaultText(statusView.message())
        );
    }

    private void showMessage(String title, String message, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    private String defaultText(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }
}
