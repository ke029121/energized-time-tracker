package me.wattguy.timetracker.ui;

import me.wattguy.timetracker.model.Session;
import me.wattguy.timetracker.util.LanguageManager;
import me.wattguy.timetracker.util.TimeUtil;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class StatsPanel extends JPanel {
    private final JPanel daysPanel;
    private final ChartPanel chartPanel;
    private LocalDate selectedStatDate = null;
    private final List<Session> allSessions;
    private static final Color ACCENT_COLOR = new Color(75, 110, 175);

    public StatsPanel(List<Session> allSessions) {
        this.allSessions = allSessions;
        setLayout(new BorderLayout());
        daysPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        chartPanel = new ChartPanel();

        JScrollPane scroll = new JScrollPane(chartPanel);
        scroll.setBorder(null);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        add(daysPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        daysPanel.removeAll();
        addBtn(LanguageManager.get("stats.button.all_time"), null);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(i);
            if (allSessions.stream().anyMatch(s -> s.getDate().equals(d))) {
                addBtn(d.format(DateTimeFormatter.ofPattern("dd.MM")), d);
            }
        }
        daysPanel.revalidate();
        daysPanel.repaint();
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    public void updateTexts() {
        refresh();
    }

    private void addBtn(String title, LocalDate date) {
        long duration = allSessions.stream()
                .filter(s -> date == null || s.getDate().equals(date))
                .mapToLong(Session::getDurationSeconds)
                .sum();

        String timeStr = TimeUtil.formatSmartDuration(duration);
        JToggleButton btn = new JToggleButton("<html><center>" + title + "<br><span style='font-size:10px; color:gray'>" + timeStr + "</span></center></html>");
        btn.setPreferredSize(new Dimension(80, 50));
        btn.setFocusPainted(false);
        btn.addActionListener(e -> {
            selectedStatDate = date;
            for (Component c : daysPanel.getComponents()) {
                if (c instanceof JToggleButton && c != btn) ((JToggleButton)c).setSelected(false);
            }
            chartPanel.revalidate();
            chartPanel.repaint();
        });
        if (date == selectedStatDate) btn.setSelected(true);
        daysPanel.add(btn);
    }

    class ChartPanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            Map<String, Long> data = getData();
            int width = Math.max(getParent().getWidth(), data.size() * 100 + 100);
            return new Dimension(width, getParent().getHeight());
        }

        private Map<String, Long> getData() {
            Map<String, Long> stats = new TreeMap<>();
            for (Session s : allSessions) {
                if (selectedStatDate != null && !s.getDate().equals(selectedStatDate)) continue;
                String name = s.getNote().replaceAll("\\s*\\(\\d+\\)$", "").trim();
                if (name.isEmpty()) name = "Без названия";
                stats.merge(name, s.getDurationSeconds(), Long::sum);
            }
            return stats.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Map<String, Long> data = getData();
            if (data.isEmpty()) {
                String noDataText = LanguageManager.get("stats.no_data");
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(noDataText);
                g2.drawString(noDataText, (getWidth() - textWidth) / 2, getHeight() / 2);
                return;
            }

            long max = data.values().stream().findFirst().orElse(1L);
            int h = getHeight();
            int x = 50;
            int barW = 60;
            int gap = 40;

            for (Map.Entry<String, Long> e : data.entrySet()) {
                int barH = (int) ((double) e.getValue() / max * (h - 100));

                g2.setColor(ACCENT_COLOR);
                g2.fillRoundRect(x, h - 50 - barH, barW, barH, 10, 10);

                g2.setColor(UIManager.getColor("Label.foreground"));
                String label = e.getKey();
                if (label.length() > 10) label = label.substring(0, 8) + "..";
                g2.drawString(label, x, h - 30);

                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.drawString(TimeUtil.formatSmartDuration(e.getValue()), x, h - 55 - barH);

                x += barW + gap;
            }
        }
    }
}