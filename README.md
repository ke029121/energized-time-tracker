# Energized Time Tracker

A simple and powerful desktop time tracker for tracking time spent on various tasks.

[Русская версия (Russian version)](README_RU.md)

## Features

### 1. Time Tracking
- **Start/Stop Timer:** Easily start and stop tracking time for any task.
- **Task Description:** Add notes to each session to know what you were working on.
- **Auto-naming:** If you start a new session with the same name, a number will be automatically appended (e.g., "Work (2)").

### 2. Global Control
- **Global Hotkey:** Start and stop the timer from anywhere in the system using the `Ctrl+Shift+T` shortcut.
- **System Tray:** The application minimizes to the tray, where you can control the timer, open the window, or close the program. The tray icon changes color depending on whether the timer is running.
- **Popup Notifications:** Receive unobtrusive notifications about timer start and stop.

### 3. Activity Tracking
- **AFK Tracker (Away From Keyboard):** The timer can automatically stop if you are inactive at the computer (no mouse movement or key presses). Idle time is configurable.

### 4. History and Editing
- **View History:** All sessions are saved and grouped by day. You can view history for any day.
- **Edit Entries:** In history, you can change the start time, end time, and description for any session.
- **Delete Entries:** Unnecessary sessions can be easily deleted.

### 5. Statistics
- **Data Visualization:** View statistics on time spent on different projects in the form of visual charts.
- **Time Filtering:** Analyze data for today, the last 7 days, or all time.

### 6. Usability
- **Autocomplete:** When entering a task name, the application suggests options from existing projects.
- **Data Storage:** All data is stored locally in TOML format in the `%LOCALAPPDATA%/TimeTracker` folder.
- **Modern Interface:** Dark theme based on FlatLaf.
