package me.wattguy.timetracker.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

public class ToastNotification extends JWindow {
    private static final Color ACTIVE_TRAY_COLOR = new Color(76, 175, 80);

    public ToastNotification(String title, String message, boolean isStart) {
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBackground(new Color(0, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout(15, 5));
        content.setBackground(new Color(40, 40, 40));
        content.setBorder(new EmptyBorder(10, 15, 10, 15));
        content.setPreferredSize(new Dimension(320, 80));

        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isStart) {
                    g2.setColor(ACTIVE_TRAY_COLOR);
                    Polygon p = new Polygon();
                    p.addPoint(0, 0);
                    p.addPoint(0, 24);
                    p.addPoint(20, 12);
                    g2.fillPolygon(p);
                } else {
                    g2.setColor(Color.ORANGE);
                    g2.fillRect(0, 0, 8, 24);
                    g2.fillRect(12, 0, 8, 24);
                }
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(24, 24));

        JPanel iconWrapper = new JPanel(new GridBagLayout());
        iconWrapper.setOpaque(false);
        iconWrapper.add(iconPanel);
        content.add(iconWrapper, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);

        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        msgLabel.setForeground(Color.LIGHT_GRAY);

        textPanel.add(titleLabel);
        textPanel.add(msgLabel);
        content.add(textPanel, BorderLayout.CENTER);

        setContentPane(content);
        pack();

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int x = scr.width - getWidth() - insets.right - 20;
        int y = scr.height - getHeight() - insets.bottom - 20;
        setLocation(x, y);
    }

    public void showToast() {
        setVisible(true);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    dispose();
                });
            }
        }, 3000);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(40, 40, 40));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        super.paint(g);
    }
}