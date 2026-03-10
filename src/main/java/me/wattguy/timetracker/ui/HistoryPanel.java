package me.wattguy.timetracker.ui;

import me.wattguy.timetracker.model.Session;
import me.wattguy.timetracker.util.LanguageManager;
import me.wattguy.timetracker.util.TimeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryPanel extends JPanel {
    private final JList<LocalDate> dayList;
    private final DefaultListModel<LocalDate> dayListModel;
    private final JTable sessionTable;
    private final DefaultTableModel tableModel;
    private LocalDate selectedDate;
    private boolean isUpdatingTable = false;
    private final List<Session> allSessions;
    private final Runnable onDataChanged;
    private static final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private final JMenuItem delItem;

    public HistoryPanel(List<Session> allSessions, Runnable onDataChanged) {
        this.allSessions = allSessions;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout());
        dayListModel = new DefaultListModel<>();
        dayList = new JList<>(dayListModel);
        dayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dayList.setCellRenderer(new DayListRenderer());
        dayList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && dayList.getSelectedValue() != null) {
                selectedDate = dayList.getSelectedValue();
                updateTableForSelectedDate();
            }
        });

        JScrollPane listScroll = new JScrollPane(dayList);
        listScroll.setPreferredSize(new Dimension(240, 0));

        tableModel = new DefaultTableModel(new String[4], 0) {
            @Override public boolean isCellEditable(int row, int column) { return column != 2; }
        };
        sessionTable = new JTable(tableModel);
        sessionTable.setRowHeight(32);
        sessionTable.setFont(MAIN_FONT);
        sessionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));

        sessionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        sessionTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del");
        sessionTable.getActionMap().put("del", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { deleteSelectedSession(); } });

        JPopupMenu popup = new JPopupMenu();
        delItem = new JMenuItem();
        delItem.addActionListener(e -> deleteSelectedSession());
        popup.add(delItem);
        sessionTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            private void showPopup(MouseEvent e) {
                int row = sessionTable.rowAtPoint(e.getPoint());
                if (row >= 0 && !sessionTable.isRowSelected(row)) {
                    sessionTable.setRowSelectionInterval(row, row);
                }
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        tableModel.addTableModelListener(e -> {
            if (!isUpdatingTable && e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                if (row >= 0) updateSessionFromTable(row);
            }
        });

        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, new JScrollPane(sessionTable)), BorderLayout.CENTER);
        updateTexts();
    }

    public void updateTexts() {
        String[] columns = {
            LanguageManager.get("history.column.start"),
            LanguageManager.get("history.column.end"),
            LanguageManager.get("history.column.duration"),
            LanguageManager.get("history.column.note")
        };
        tableModel.setColumnIdentifiers(columns);
        delItem.setText(LanguageManager.get("history.menu.delete"));
        dayList.repaint();
    }

    public void refreshData() {
        LocalDate prev = dayList.getSelectedValue();
        Set<LocalDate> dates = allSessions.stream().map(Session::getDate).collect(Collectors.toCollection(() -> new TreeSet<>((d1, d2) -> d2.compareTo(d1))));
        dayListModel.clear();
        dates.forEach(dayListModel::addElement);
        if (prev != null && dates.contains(prev)) dayList.setSelectedValue(prev, true);
        else if (!dayListModel.isEmpty()) dayList.setSelectedIndex(0);
        else tableModel.setRowCount(0);
    }

    private void updateTableForSelectedDate() {
        isUpdatingTable = true;
        tableModel.setRowCount(0);
        if (selectedDate != null) {
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
            allSessions.stream().filter(s -> s.getDate().equals(selectedDate))
                    .sorted(Comparator.comparing(Session::getStart).reversed())
                    .forEach(s -> tableModel.addRow(new Object[]{
                            s.getStart().format(timeFmt),
                            s.getEnd().format(timeFmt),
                            TimeUtil.formatDuration(s.getDurationSeconds()),
                            s.getNote()
                    }));
        }
        isUpdatingTable = false;
    }

    private void updateSessionFromTable(int row) {
        if (selectedDate == null) return;
        List<Session> daily = allSessions.stream().filter(s -> s.getDate().equals(selectedDate))
                .sorted(Comparator.comparing(Session::getStart).reversed()).collect(Collectors.toList());
        if (row >= daily.size()) return;

        Session s = daily.get(row);
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
            s.setStart(LocalDateTime.of(selectedDate, LocalTime.parse((String)tableModel.getValueAt(row, 0), fmt)));
            s.setEnd(LocalDateTime.of(selectedDate, LocalTime.parse((String)tableModel.getValueAt(row, 1), fmt)));
            s.setNote((String)tableModel.getValueAt(row, 3));

            isUpdatingTable = true;
            tableModel.setValueAt(TimeUtil.formatDuration(s.getDurationSeconds()), row, 2);
            isUpdatingTable = false;

            onDataChanged.run();
            dayList.repaint();
        } catch (Exception e) {
            updateTableForSelectedDate();
        }
    }

    private void deleteSelectedSession() {
        int[] rows = sessionTable.getSelectedRows();
        if (rows.length == 0) return;

        String message = String.format(LanguageManager.get("history.confirm.delete"), rows.length);
        if (JOptionPane.showConfirmDialog(this, message, LanguageManager.get("history.confirm.title"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            List<Session> daily = allSessions.stream().filter(s -> s.getDate().equals(selectedDate))
                    .sorted(Comparator.comparing(Session::getStart).reversed()).collect(Collectors.toList());

            List<Session> toRemove = new ArrayList<>();
            for (int row : rows) {
                if (row < daily.size()) {
                    toRemove.add(daily.get(row));
                }
            }
            allSessions.removeAll(toRemove);
            onDataChanged.run();
            refreshData();
        }
    }

    class DayListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(5, 5));
            p.setBorder(new EmptyBorder(5, 10, 5, 10));
            p.setBackground(isSelected ? UIManager.getColor("List.selectionBackground") : UIManager.getColor("List.background"));

            LocalDate d = (LocalDate) value;
            long sum = allSessions.stream().filter(s -> s.getDate().equals(d)).mapToLong(Session::getDurationSeconds).sum();

            // Format date based on locale
            String dateStr;
            if (LanguageManager.getCurrentLocale().getLanguage().equals("ru")) {
                dateStr = d.format(DateTimeFormatter.ofPattern("dd MMMM", new Locale("ru")));
            } else {
                dateStr = d.format(DateTimeFormatter.ofPattern("MMMM dd", Locale.ENGLISH));
            }

            JLabel l1 = new JLabel(dateStr);
            l1.setFont(new Font("Segoe UI", Font.BOLD, 14));
            l1.setForeground(isSelected ? UIManager.getColor("List.selectionForeground") : UIManager.getColor("Label.foreground"));

            JLabel l2 = new JLabel(TimeUtil.formatDuration(sum));
            l2.setForeground(UIManager.getColor("Label.disabledForeground"));

            p.add(l1, BorderLayout.CENTER);
            p.add(l2, BorderLayout.SOUTH);
            return p;
        }
    }
}