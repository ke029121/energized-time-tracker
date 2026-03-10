package me.wattguy.timetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.formdev.flatlaf.FlatDarkLaf;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import lombok.Data;
import me.wattguy.timetracker.model.Session;
import me.wattguy.timetracker.model.SessionList;
import me.wattguy.timetracker.ui.AutocompleteKeyListener;
import me.wattguy.timetracker.ui.HistoryPanel;
import me.wattguy.timetracker.ui.StatsPanel;
import me.wattguy.timetracker.ui.ToastNotification;
import me.wattguy.timetracker.util.LanguageManager;
import me.wattguy.timetracker.util.TimeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main extends JFrame implements NativeKeyListener, NativeMouseInputListener {

    private final List<Session> allSessions = new ArrayList<>();
    private Session currentActiveSession = null;
    private final Path dataRoot;
    private final Path settingsPath;
    private final ObjectMapper tomlMapper;
    private final Set<String> projectNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private long lastActivityTime = System.currentTimeMillis();
    private Timer afkCheckTimer;
    private boolean isAfk = false;

    private JLabel timeLabel;
    private JLabel statusLabel;
    private JLabel detailsLabel;
    private JTextField noteField;
    private JButton toggleButton;
    private JSpinner afkSpinner;
    private JLabel afkLabel;

    private HistoryPanel historyPanel;
    private StatsPanel statsPanel;
    private Timer uiTimer;

    private SystemTray tray;
    private TrayIcon trayIcon;
    private PopupMenu trayPopupMenu;
    private MenuItem openTrayItem;
    private MenuItem toggleTrayItem;
    private MenuItem exitTrayItem;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color ACCENT_COLOR = new Color(75, 110, 175);
    private static final Color STOP_COLOR = new Color(180, 60, 60);
    private static final Color ACTIVE_TRAY_COLOR = new Color(76, 175, 80);
    private static final Color INACTIVE_TRAY_COLOR = new Color(158, 158, 158);
    private static final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private JTabbedPane tabbedPane;
    private JMenu settingsMenu;
    private JMenu languageMenu;

    @Data
    public static class AppSettings {
        private String language;
    }

    public Main() {
        tomlMapper = new TomlMapper();
        tomlMapper.registerModule(new JavaTimeModule());
        tomlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tomlMapper.enable(SerializationFeature.INDENT_OUTPUT);

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) localAppData = System.getProperty("user.home");
        dataRoot = Paths.get(localAppData, "TimeTracker", "data");
        settingsPath = Paths.get(localAppData, "TimeTracker", "settings.toml");

        loadSettings();
        loadAllData();
        updateProjectNameCache();

        setupUI();
        setupSystemTray();
        setupGlobalHooks();
        setupTimers();
        setupWindowListener();

        updateTexts();
    }

    private void loadSettings() {
        if (Files.exists(settingsPath)) {
            try {
                AppSettings settings = tomlMapper.readValue(settingsPath.toFile(), AppSettings.class);
                if (settings.getLanguage() != null) {
                    LanguageManager.setLocale(new Locale(settings.getLanguage()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveSettings() {
        try {
            Files.createDirectories(settingsPath.getParent());
            AppSettings settings = new AppSettings();
            settings.setLanguage(LanguageManager.getCurrentLocale().getLanguage());
            tomlMapper.writeValue(settingsPath.toFile(), settings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupUI() {
        setIconImage(createAppIcon());
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setupMenuBar();

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(MAIN_FONT);

        JPanel timerPanel = createTimerPanel();
        historyPanel = new HistoryPanel(allSessions, this::saveTodayData);
        statsPanel = new StatsPanel(allSessions);

        tabbedPane.addTab("", timerPanel);
        tabbedPane.addTab("", historyPanel);
        tabbedPane.addTab("", statsPanel);

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == historyPanel) historyPanel.refreshData();
            else if (tabbedPane.getSelectedComponent() == statsPanel) statsPanel.refresh();
        });

        add(tabbedPane);
        getRootPane().setDefaultButton(toggleButton);
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        settingsMenu = new JMenu();
        languageMenu = new JMenu();
        settingsMenu.add(languageMenu);

        JRadioButtonMenuItem enItem = new JRadioButtonMenuItem("English");
        enItem.addActionListener(e -> switchLanguage(Locale.ENGLISH));
        JRadioButtonMenuItem ruItem = new JRadioButtonMenuItem("Русский");
        ruItem.addActionListener(e -> switchLanguage(new Locale("ru")));

        ButtonGroup group = new ButtonGroup();
        group.add(enItem);
        group.add(ruItem);
        languageMenu.add(enItem);
        languageMenu.add(ruItem);

        if (LanguageManager.getCurrentLocale().getLanguage().equals("ru")) {
            ruItem.setSelected(true);
        } else {
            enItem.setSelected(true);
        }

        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);
    }

    private void switchLanguage(Locale locale) {
        LanguageManager.setLocale(locale);
        saveSettings();
        updateTexts();
    }

    private void updateTexts() {
        setTitle(LanguageManager.get("app.title"));

        // Tabs
        tabbedPane.setTitleAt(0, LanguageManager.get("timer.tab"));
        tabbedPane.setTitleAt(1, LanguageManager.get("history.tab"));
        tabbedPane.setTitleAt(2, LanguageManager.get("stats.tab"));

        // Timer Panel
        noteField.putClientProperty("JTextField.placeholderText", LanguageManager.get("timer.note.placeholder"));
        afkLabel.setText(LanguageManager.get("timer.afk.label"));
        updateUIState(currentActiveSession != null);

        // Menu
        settingsMenu.setText(LanguageManager.get("menu.settings"));
        languageMenu.setText(LanguageManager.get("settings.language"));

        // Dialogs
        UIManager.put("OptionPane.yesButtonText", LanguageManager.get("dialog.yes"));
        UIManager.put("OptionPane.noButtonText", LanguageManager.get("dialog.no"));

        // Panels
        historyPanel.updateTexts();
        statsPanel.updateTexts();

        // Tray
        updateTrayTexts();
    }

    private void setupTimers() {
        uiTimer = new Timer();
        uiTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> updateTimerUI());
            }
        }, 0, 1000);

        afkCheckTimer = new Timer();
        afkCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAfk();
            }
        }, 10000, 10000);
    }

    private void setupWindowListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (currentActiveSession != null) stopSession();
                saveTodayData();
            }
            @Override
            public void windowIconified(WindowEvent e) {
                setVisible(false);
            }
        });
    }

    private BufferedImage createAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ACCENT_COLOR);
        g2.fillRoundRect(0, 0, 64, 64, 20, 20);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(4));
        g2.drawOval(12, 12, 40, 40);
        g2.drawLine(32, 32, 32, 20);
        g2.drawLine(32, 32, 42, 32);
        g2.dispose();
        return image;
    }

    private BufferedImage createTrayIcon(boolean isActive) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isActive ? ACTIVE_TRAY_COLOR : INACTIVE_TRAY_COLOR);
        g2.fillOval(1, 1, 14, 14);
        g2.setColor(Color.WHITE);
        if (isActive) {
            Polygon p = new Polygon();
            p.addPoint(6, 4);
            p.addPoint(6, 12);
            p.addPoint(12, 8);
            g2.fillPolygon(p);
        } else {
            g2.fillRect(5, 4, 2, 8);
            g2.fillRect(9, 4, 2, 8);
        }
        g2.dispose();
        return image;
    }

    private JPanel createTimerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 20, 10, 20);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 18));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridy = 0;
        panel.add(statusLabel, gbc);

        timeLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 80));
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 20, 10, 20);
        panel.add(timeLabel, gbc);

        detailsLabel = new JLabel(" ", SwingConstants.CENTER);
        detailsLabel.setFont(MAIN_FONT);
        detailsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 20, 20, 20);
        panel.add(detailsLabel, gbc);

        noteField = new JTextField();
        noteField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        noteField.addKeyListener(new AutocompleteKeyListener(noteField, projectNames));
        gbc.gridy = 3;
        panel.add(noteField, gbc);

        JPanel afkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        afkLabel = new JLabel();
        afkLabel.setFont(MAIN_FONT);
        afkSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        afkSpinner.setFont(MAIN_FONT);
        afkPanel.add(afkLabel);
        afkPanel.add(afkSpinner);

        gbc.gridy = 4;
        panel.add(afkPanel, gbc);

        toggleButton = new JButton();
        toggleButton.setFont(new Font("Segoe UI", Font.BOLD, 20));
        toggleButton.setForeground(Color.WHITE);
        toggleButton.setFocusPainted(false);
        toggleButton.setBorder(new EmptyBorder(15, 30, 15, 30));
        toggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleButton.addActionListener(e -> toggleSession());

        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(toggleButton, gbc);

        return panel;
    }

    private void toggleSession() {
        if (currentActiveSession == null) startSession();
        else stopSession();
    }

    private void startSession() {
        String baseNote = noteField.getText().trim();
        if (baseNote.isEmpty()) baseNote = LanguageManager.get("timer.note.default");

        LocalDate today = LocalDate.now();
        String finalBaseNote = baseNote;
        long count = allSessions.stream()
            .filter(s -> s.getDate().equals(today) && s.getNote().startsWith(finalBaseNote))
            .count();

        String finalNote = (count > 0) ? baseNote + " (" + (count + 1) + ")" : baseNote;

        currentActiveSession = new Session(LocalDateTime.now(), null, finalNote);
        lastActivityTime = System.currentTimeMillis();
        isAfk = false;

        updateUIState(true);
        showNotification(LanguageManager.get("notification.timer_started.title"), finalNote, true);
        updateTrayTooltip();
    }

    private void stopSession() {
        if (currentActiveSession == null) return;

        currentActiveSession.setEnd(LocalDateTime.now());
        allSessions.add(currentActiveSession);
        updateProjectNameCache();
        currentActiveSession = null;

        updateUIState(false);
        saveTodayData();
        showNotification(LanguageManager.get("notification.timer_stopped.title"), LanguageManager.get("notification.timer_stopped.message"), false);
        updateTrayTooltip();
    }

    private void updateUIState(boolean running) {
        if (running) {
            toggleButton.setText(LanguageManager.get("timer.button.stop"));
            toggleButton.setBackground(STOP_COLOR);
            statusLabel.setText(String.format(LanguageManager.get("timer.status.in_progress"), currentActiveSession.getNote()));
            statusLabel.setForeground(new Color(100, 200, 100));
            noteField.setEnabled(false);
            afkSpinner.setEnabled(false);
            updateTrayMenu(LanguageManager.get("tray.menu.stop"));
            if (trayIcon != null) trayIcon.setImage(createTrayIcon(true));
        } else {
            toggleButton.setText(LanguageManager.get("timer.button.start"));
            toggleButton.setBackground(ACCENT_COLOR);
            timeLabel.setText("00:00:00");
            statusLabel.setText(LanguageManager.get("timer.status.no_session"));
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            detailsLabel.setText(" ");
            noteField.setEnabled(true);
            afkSpinner.setEnabled(true);
            updateTrayMenu(LanguageManager.get("tray.menu.start"));
            if (trayIcon != null) trayIcon.setImage(createTrayIcon(false));
        }
    }

    private void updateTimerUI() {
        if (currentActiveSession != null) {
            long seconds = Duration.between(currentActiveSession.getStart(), LocalDateTime.now()).getSeconds();
            timeLabel.setText(TimeUtil.formatDuration(seconds));

            long todayTotal = allSessions.stream()
                    .filter(s -> s.getDate().equals(LocalDate.now()))
                    .mapToLong(Session::getDurationSeconds)
                    .sum() + seconds;

            detailsLabel.setText(String.format(LanguageManager.get("timer.details.start"), currentActiveSession.getStart().format(timeFormatter))
                    + "  |  " + String.format(LanguageManager.get("timer.details.total_today"), TimeUtil.formatDuration(todayTotal)));

            updateTrayTooltip();
        }
    }

    private void checkAfk() {
        if (currentActiveSession == null || isAfk) return;
        long idleMillis = System.currentTimeMillis() - lastActivityTime;
        int limit = (int) afkSpinner.getValue();
        if (idleMillis > limit * 60 * 1000L) {
            isAfk = true;
            SwingUtilities.invokeLater(() -> {
                stopSession();
                String message = String.format(LanguageManager.get("notification.afk.message"), limit);
                showNotification(LanguageManager.get("notification.afk.title"), message, false);
            });
        }
    }

    private void updateActivity() {
        lastActivityTime = System.currentTimeMillis();
        isAfk = false;
    }

    private void updateProjectNameCache() {
        projectNames.clear();
        allSessions.forEach(s -> projectNames.add(s.getNote().replaceAll("\\s*\\(\\d+\\)$", "").trim()));
    }

    private void saveTodayData() {
        try {
            LocalDate today = LocalDate.now();
            Path dayDir = dataRoot.resolve(today.toString());
            Files.createDirectories(dayDir);

            List<Session> todaySessions = allSessions.stream()
                    .filter(s -> s.getDate().equals(today))
                    .collect(Collectors.toList());

            SessionList wrapper = new SessionList();
            wrapper.setSessions(todaySessions);

            tomlMapper.writeValue(dayDir.resolve("sessions.toml").toFile(), wrapper);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadAllData() {
        allSessions.clear();
        if (!Files.exists(dataRoot)) return;

        try (Stream<Path> days = Files.list(dataRoot)) {
            days.filter(Files::isDirectory).forEach(dayDir -> {
                File file = dayDir.resolve("sessions.toml").toFile();
                if (file.exists()) {
                    try {
                        SessionList wrapper = tomlMapper.readValue(file, SessionList.class);
                        if (wrapper.getSessions() != null) {
                            allSessions.addAll(wrapper.getSessions());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;
        tray = SystemTray.getSystemTray();

        trayPopupMenu = new PopupMenu();
        openTrayItem = new MenuItem();
        openTrayItem.addActionListener(e -> { setVisible(true); setExtendedState(NORMAL); toFront(); });

        toggleTrayItem = new MenuItem();
        toggleTrayItem.addActionListener(e -> SwingUtilities.invokeLater(this::toggleSession));

        exitTrayItem = new MenuItem();
        exitTrayItem.addActionListener(e -> {
            if (currentActiveSession != null) stopSession();
            saveTodayData();
            System.exit(0);
        });

        trayPopupMenu.add(openTrayItem);
        trayPopupMenu.add(toggleTrayItem);
        trayPopupMenu.addSeparator();
        trayPopupMenu.add(exitTrayItem);

        trayIcon = new TrayIcon(createTrayIcon(false), LanguageManager.get("app.title"), trayPopupMenu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> { setVisible(true); setExtendedState(NORMAL); toFront(); });

        try { tray.add(trayIcon); } catch (AWTException e) { e.printStackTrace(); }
        updateTrayTexts();
    }

    private void updateTrayTexts() {
        if (tray == null) return;
        openTrayItem.setLabel(LanguageManager.get("tray.menu.open"));
        toggleTrayItem.setLabel(LanguageManager.get(currentActiveSession == null ? "tray.menu.start" : "tray.menu.stop"));
        exitTrayItem.setLabel(LanguageManager.get("tray.menu.exit"));
        updateTrayTooltip();
    }

    private void updateTrayMenu(String label) {
        if (toggleTrayItem != null) {
            toggleTrayItem.setLabel(label);
        }
    }

    private void updateTrayTooltip() {
        if (trayIcon == null) return;
        if (currentActiveSession != null) {
            long seconds = Duration.between(currentActiveSession.getStart(), LocalDateTime.now()).getSeconds();
            String tooltip = String.format(LanguageManager.get("tray.tooltip.in_progress"),
                    currentActiveSession.getNote(),
                    TimeUtil.formatDuration(seconds));
            trayIcon.setToolTip(tooltip);
        } else {
            trayIcon.setToolTip(LanguageManager.get("tray.tooltip.no_activity"));
        }
    }

    private void showNotification(String title, String message, boolean isStart) {
        if (isActive() && isVisible()) return;

        new ToastNotification(title, message, isStart).showToast();
    }

    private void setupGlobalHooks() {
        Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.OFF);
        try { GlobalScreen.registerNativeHook(); } catch (NativeHookException e) { System.exit(1); }
        GlobalScreen.addNativeKeyListener(this);
        GlobalScreen.addNativeMouseListener(this);
        GlobalScreen.addNativeMouseMotionListener(this);
    }
    @Override public void nativeKeyPressed(NativeKeyEvent e) {
        updateActivity();
        if (e.getKeyCode() == NativeKeyEvent.VC_T && (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0 && (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0) {
            SwingUtilities.invokeLater(this::toggleSession);
        }
    }
    @Override public void nativeKeyReleased(NativeKeyEvent e) { updateActivity(); }
    @Override public void nativeKeyTyped(NativeKeyEvent e) { updateActivity(); }
    @Override public void nativeMouseClicked(NativeMouseEvent e) { updateActivity(); }
    @Override public void nativeMousePressed(NativeMouseEvent e) { updateActivity(); }
    @Override public void nativeMouseReleased(NativeMouseEvent e) { updateActivity(); }
    @Override public void nativeMouseMoved(NativeMouseEvent e) { updateActivity(); }
    @Override public void nativeMouseDragged(NativeMouseEvent e) { updateActivity(); }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}