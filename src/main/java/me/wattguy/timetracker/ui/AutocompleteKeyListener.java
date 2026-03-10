package me.wattguy.timetracker.ui;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.Set;

public class AutocompleteKeyListener extends KeyAdapter {
    private final JTextField noteField;
    private final Set<String> projectNames;

    public AutocompleteKeyListener(JTextField noteField, Set<String> projectNames) {
        this.noteField = noteField;
        this.projectNames = projectNames;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.isActionKey() || e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL || e.getKeyCode() == KeyEvent.VK_ALT) return;
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE) return;

        String prefix = noteField.getText();
        if (prefix.isEmpty()) return;

        Optional<String> match = projectNames.stream()
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .findFirst();

        if (match.isPresent()) {
            String suggestion = match.get();
            if (suggestion.length() > prefix.length()) {
                SwingUtilities.invokeLater(() -> {
                    noteField.setText(suggestion);
                    noteField.select(prefix.length(), suggestion.length());
                });
            }
        }
    }
}