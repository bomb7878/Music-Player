import util.SwingUtils;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window supporting multiple playlists (tabs),
 * volume control, theme switching, and keyboard shortcuts.
 */
public class Window extends JFrame {
    private static final String PLAY_BUTTON_PLAY_ICON = "play_track.png";
    private static final String PLAY_BUTTON_STOP_ICON = "stop_track.png";
    private static final String LOOP_PLAY_OFF_ICON = "loop_play_off.png";
    private static final String LOOP_PLAY_ON_ICON = "loop_play_on.png";
    private static final String LOOP_ONE_TRACK_OFF_ICON = "loop_one_off.png";
    private static final String LOOP_ONE_TRACK_ON_ICON = "loop_one_on.png";
    private static final String MIX_PLAY_OFF_ICON = "mix_play_off.png";
    private static final String MIX_PLAY_ON_ICON = "mix_play_on.png";

    // UI components (from .form file)
    private JPanel panelMain;
    private JCheckBox mixPlayButton;
    private JButton nextTrackButton;
    private JButton playButton;
    private JCheckBox loopOneTrackButton;
    private JCheckBox loopPlayButton;
    private JButton prevTrackButton;
    private JProgressBar musicTime;
    private JPanel musicListPanel;
    private JPanel buttonsPanel;
    private JPanel playerSettingsPanel;
    private JCheckBox themeButton;
    private JButton volumeControlButton;
    private JSlider volumeControl;
    private JTabbedPane playlistsPane;
    private JLabel currentVolumeLabel;

    // Playlist data
    private Map<String, List<String>> originalMusicNames;      // playlist name → song names (no extension)
    private List<JList<String>> playlists = new ArrayList<>(); // JList components in tab order

    private final String musicDirectory = "./music/";
    private final String iconsDirectory = "./icons/";

    // Player instances per playlist
    private Map<String, MusicPlayer> players = new HashMap<>();
    private Timer progressTimer;

    // Currently active player and its corresponding list (sync with selected tab)
    private MusicPlayer currentPlayer;
    private JList<String> currentMusicList;

    private boolean isDarkTheme = false;

    public Window() {
        super("Music Player");
        setContentPane(panelMain);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 600);
        setLocationRelativeTo(null);

        musicTime.setMinimum(0);
        musicTime.setStringPainted(true);
        musicTime.setString("Select a Track!");

        currentVolumeLabel.setLabelFor(volumeControl);

        // Extract zip archives into music subdirectories (if any)
        Server.genMusicDirectories();

        // Load playlists from subdirectories
        displayDefaultPlaylists();

        // Create a MusicPlayer for each playlist
        for (Map.Entry<String, List<String>> entry : originalMusicNames.entrySet()) {
            players.put(entry.getKey(), genMusicPlayer(entry.getKey(), entry.getValue()));
        }

        // Select the first tab by default
        if (!playlists.isEmpty()) {
            currentPlayer = players.get(playlistsPane.getTitleAt(0));
            currentMusicList = playlists.getFirst();
            updateUIForCurrentPlayer();
        }

        initGlobalListeners();

        // Switch player when tab changes
        playlistsPane.addChangeListener(e -> {
            int idx = playlistsPane.getSelectedIndex();
            if (idx != -1) {
                currentPlayer = players.get(playlistsPane.getTitleAt(idx));
                currentMusicList = playlists.get(idx);
                updateUIForCurrentPlayer();
            }
        });
    }

    // ----- Playlist loading ------------------------------------------

    /**
     * Scans musicDirectory for subdirectories (playlists) and builds tabs.
     * Called after archives are extracted.
     */
    private void displayDefaultPlaylists() {
        File folder = new File(musicDirectory);
        if (!folder.exists() || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Folder " + musicDirectory + " not found!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) return;

        originalMusicNames = new HashMap<>();

        for (File file : files) {
            if (!file.isDirectory()) continue;

            DefaultListModel<String> model = new DefaultListModel<>();
            File[] musicFiles = file.listFiles();
            if (musicFiles != null) {
                for (File f : musicFiles) {
                    if (f.getName().endsWith(".mp3")) {
                        model.addElement(f.getName().replaceFirst("\\.mp3$", ""));
                    }
                }
            }
            if (model.isEmpty()) continue;

            String playlistName = file.getName();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < model.size(); i++) list.add(model.get(i));
            originalMusicNames.put(playlistName, list);

            JList<String> musicList = new JList<>(model);
            JScrollPane scrollPane = new JScrollPane(musicList);
            playlists.add(musicList);
            playlistsPane.addTab(playlistName, scrollPane);
        }
    }

    private MusicPlayer genMusicPlayer(String playlistName, List<String> playlist) {
        List<String> paths = new ArrayList<>();
        for (String song : playlist) {
            paths.add(musicDirectory + playlistName + "/" + song + ".mp3");
        }
        return new MusicPlayer(paths);
    }

    // ----- Global listeners (initialized once) -----------------------

    /**
     * Sets up all event listeners that work with the current active player and list.
     * Called once in constructor.
     */
    private void initGlobalListeners() {
        // Keyboard shortcuts: left/right seek, space toggle
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && currentPlayer != null) {
                if (currentPlayer.isPlaying()) {
                    if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        int maxStep = Math.min(10, (int) currentPlayer.getTotalDuration() - currentPlayer.getCurrentPosition());
                        currentPlayer.skipNSecond(maxStep);
                        updateProgressBar();
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                        currentPlayer.skipNSecond(-10);
                        updateProgressBar();
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                        togglePlayPause();
                        return true;
                    }
                } else {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                        togglePlayPause();
                        return true;
                    }
                }
            }
            return false;
        });

        // Progress timer (updates every 100ms)
        progressTimer = new Timer(100, e -> {
            // Re‑apply current volume to ensure consistency
            currentPlayer.setVolume(volumeControl.getValue() / 100f);

            if (!currentPlayer.isPlaying() && !currentPlayer.isPaused()) {
                progressTimer.stop();
                musicTime.setValue(0);
                musicTime.setString("00:00");
                changeIcon(playButton, PLAY_BUTTON_PLAY_ICON);
            } else {
                int cur = currentPlayer.getCurrentPosition();
                int total = (int) currentPlayer.getTotalDuration();
                if (total > 0) {
                    musicTime.setValue(Math.min(cur, total));
                    musicTime.setString(formatTime(cur));
                    if (currentMusicList != null) {
                        currentMusicList.setSelectedIndex(currentPlayer.getCurrentTrackIndex());
                    }
                }
            }
        });
        progressTimer.start();

        // Play / Pause button
        playButton.addActionListener(e -> togglePlayPause());

        // Single click on playlist item starts playback
        for(JList<String> playlist: playlists) {
            playlist.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                        int idx = playlist.locationToIndex(e.getPoint());
                        if (idx != -1) play(idx);
                    }
                }
            });
        }

        // Previous / Next track buttons
        prevTrackButton.addActionListener(e -> {
            if (currentPlayer == null) return;
            int idx = currentPlayer.getCurrentTrackIndex();
            if (idx == -1) return;

            int cur = currentPlayer.getCurrentPosition();
            int total = (int) currentPlayer.getTotalDuration();
            if (cur > total / 2) {
                int prev = Math.max(currentPlayer.getPrevTrackIndex(), 0);
                currentPlayer.playMusic(prev);
                if (currentMusicList != null) currentMusicList.setSelectedIndex(prev);
            } else {
                currentPlayer.playMusic(idx);
            }
        });

        nextTrackButton.addActionListener(e -> {
            if (currentPlayer == null) return;
            int idx = currentPlayer.getCurrentTrackIndex();
            if (idx == -1) return;
            int next = Math.min(currentPlayer.getNextTrackIndex(), currentPlayer.size());
            currentPlayer.playMusic(next);
            if (currentMusicList != null) currentMusicList.setSelectedIndex(next);
        });

        // Shuffle toggle
        mixPlayButton.addActionListener(e -> {
            boolean enable = mixPlayButton.isSelected();
            shufflePlaylist(enable);
            setButtonTooltips(mixPlayButton, enable, "Shuffle on", "Shuffle off");
            if(enable) {
                changeIcon(mixPlayButton, MIX_PLAY_ON_ICON);
            } else {
                changeIcon(mixPlayButton, MIX_PLAY_OFF_ICON);
            }
        });

        // Repeat modes
        loopPlayButton.addActionListener(e -> {
            if (currentPlayer != null) currentPlayer.setLoopingPlaylist();
            boolean enable = loopPlayButton.isSelected();

            setButtonTooltips(loopPlayButton, enable,
                    "Playlist repeat on", "Playlist repeat off");

            if(enable) {
                changeIcon(loopPlayButton, LOOP_PLAY_ON_ICON);
            } else {
                changeIcon(loopPlayButton, LOOP_PLAY_OFF_ICON);
            }
        });

        loopOneTrackButton.addActionListener(e -> {
            if (currentPlayer != null) currentPlayer.setLoopingOneTrack();
            boolean enable = loopOneTrackButton.isSelected();

            setButtonTooltips(loopOneTrackButton, enable,
                    "Single track repeat on", "Single track repeat off");

            if(enable) {
                changeIcon(loopOneTrackButton, LOOP_ONE_TRACK_ON_ICON);
            } else {
                changeIcon(loopOneTrackButton, LOOP_ONE_TRACK_OFF_ICON);
            }
        });

        // Theme toggle
        themeButton.addActionListener(e -> {
            if (!isDarkTheme) {
                // Apply dark theme
                panelMain.setBackground(Colors.MAIN_BACKGROUND_DARK_THEME.getColor());
                buttonsPanel.setBackground(Colors.PANELS_DARK_THEME.getColor());
                playerSettingsPanel.setBackground(Colors.PANELS_DARK_THEME.getColor());
                musicListPanel.setBackground(Colors.PANELS_DARK_THEME.getColor());

                for (JButton btn : new JButton[]{playButton, nextTrackButton, prevTrackButton, volumeControlButton}) {
                    btn.setBackground(Colors.BUTTONS_DARK_THEME.getColor());
                    btn.setForeground(Colors.BASIC_TEXT_DARK_THEME.getColor());
                }
                for (JCheckBox box : new JCheckBox[]{mixPlayButton, loopPlayButton, loopOneTrackButton, themeButton}) {
                    box.setBackground(Colors.BUTTONS_DARK_THEME.getColor());
                    box.setForeground(Colors.BASIC_TEXT_DARK_THEME.getColor());
                }
                // Dark theme for all playlists
                for (int i = 0; i < playlists.size(); i++) {
                    JList<String> list = playlists.get(i);

                    list.setBackground(Colors.MUSIC_LIST_DARK_THEME.getColor());
                    list.setForeground(Colors.BASIC_TEXT_DARK_THEME.getColor());
                    list.setSelectionBackground(Colors.HIGHLIGHTED_CELL_DARK_THEME.getColor());
                    list.setSelectionForeground(Colors.ACTIVE_MUSIC_TEXT_DARK_THEME.getColor());

                    playlistsPane.setBackgroundAt(i, Colors.PANELS_DARK_THEME.getColor());
                    playlistsPane.setForegroundAt(i, Colors.BASIC_TEXT_DARK_THEME.getColor());
                }

                musicTime.setBackground(Colors.MUSIC_TIME_BACKGROUND_DARK_THEME.getColor());
                musicTime.setForeground(Colors.ACCENT_DARK_THEME.getColor());

                volumeControl.setBackground(Colors.BUTTONS_DARK_THEME.getColor());
                volumeControl.setForeground(Colors.ACCENT_DARK_THEME.getColor());
                currentVolumeLabel.setForeground(Colors.BASIC_TEXT_DARK_THEME.getColor());

                isDarkTheme = true;
            } else {
                // Revert to default (light) theme
                Colors.toDefault(panelMain);
                Colors.toDefault(buttonsPanel);
                for (JButton btn : new JButton[]{playButton, nextTrackButton, prevTrackButton, volumeControlButton})
                    Colors.toDefault(btn);
                for (JCheckBox box : new JCheckBox[]{mixPlayButton, loopPlayButton, loopOneTrackButton, themeButton})
                    Colors.toDefault(box);
                Colors.toDefault(playerSettingsPanel);
                Colors.toDefault(musicListPanel);

                // Light theme for all playlists
                for (int i = 0; i < playlists.size(); i++) {
                    JList<String> list = playlists.get(i);

                    Colors.toDefault(list);

                    playlistsPane.setBackgroundAt(i, null);  // null = default
                    playlistsPane.setForegroundAt(i, null);
                }

                Colors.toDefault(musicTime);
                Colors.toDefault(volumeControl);
                isDarkTheme = false;
            }
        });

        // Show/hide volume slider
        volumeControlButton.addActionListener(e -> {
            volumeControl.setVisible(!volumeControl.isVisible());
            currentVolumeLabel.setVisible(!currentVolumeLabel.isVisible());
        });

        // Volume change
        volumeControl.addChangeListener(e -> {
            if (!volumeControl.getValueIsAdjusting() && currentPlayer != null) {
                float vol = volumeControl.getValue() / 100f;
                currentPlayer.setVolume(vol);
                currentVolumeLabel.setText(volumeControl.getValue() + "%");
            }
        });
    }

    // ----- UI update helpers -----------------------------------------

    /** Updates play icon, progress bar, and selected track for the current player. */
    private void updateUIForCurrentPlayer() {
        if (currentPlayer == null) return;

        changeIcon(playButton, currentPlayer.isPlaying() ? PLAY_BUTTON_STOP_ICON : PLAY_BUTTON_PLAY_ICON);

        int cur = currentPlayer.getCurrentPosition();
        int total = (int) currentPlayer.getTotalDuration();
        if (total > 0) {
            musicTime.setValue(Math.min(cur, total));
            musicTime.setString(formatTime(cur));
        } else {
            musicTime.setValue(0);
            musicTime.setString("00:00");
        }

        int idx = currentPlayer.getCurrentTrackIndex();
        if (idx != -1 && currentMusicList != null) {
            currentMusicList.setSelectedIndex(idx);
        }
    }

    private void updateProgressBar() {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            musicTime.setValue(currentPlayer.getCurrentPosition());
            musicTime.setString(formatTime(currentPlayer.getCurrentPosition()));
        }
    }

    // ----- Utilities -------------------------------------------------

    private <T extends AbstractButton> void setButtonTooltips(T btn, boolean enabled, String on, String off) {
        btn.setToolTipText(enabled ? on : off);
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private<T extends AbstractButton> void changeIcon(T button, String fileName) {
        button.setIcon(new ImageIcon(iconsDirectory + fileName));
    }

    // ----- Playback control ------------------------------------------

    private void play(int index) {
        if (currentPlayer == null) return;

        if (currentPlayer.isPlaying()) {
            currentPlayer.stopMusic();
            changeIcon(playButton, PLAY_BUTTON_PLAY_ICON);
        } else {
            currentPlayer.playMusic(index);
            changeIcon(playButton, PLAY_BUTTON_STOP_ICON);
            if (!progressTimer.isRunning()) progressTimer.start();
        }
    }

    private void togglePlayPause() {
        if (currentPlayer == null || currentMusicList == null) return;

        if (currentPlayer.isPlaying() || currentPlayer.isPaused()) {
            currentPlayer.togglePause();
            updateProgressBar();
            if (currentPlayer.isPaused()) changeIcon(playButton, PLAY_BUTTON_PLAY_ICON);
            else if (currentPlayer.isPlaying()) changeIcon(playButton, PLAY_BUTTON_STOP_ICON);
        } else {
            int idx = currentMusicList.getSelectedIndex();
            if (idx == -1) {
                idx = 0;
                currentMusicList.setSelectedIndex(0);
            }
            play(idx);
        }
    }

    /**
     * Shuffles the current playlist. When enabled, the selected track (if any)
     * is kept and moved to the top. When disabled, restores the original order.
     */
    private void shufflePlaylist(boolean enable) {
        if (currentMusicList == null || currentPlayer == null) return;

        DefaultListModel<String> model = (DefaultListModel<String>) currentMusicList.getModel();
        List<String> names = new ArrayList<>();

        if (enable) {
            int selIdx = currentMusicList.getSelectedIndex();
            String selected = (selIdx != -1) ? model.get(selIdx) : null;
            for (int i = 0; i < model.size(); i++) names.add(model.get(i));
            if (selected != null) names.remove(selected);
            Collections.shuffle(names);
            if (selected != null) names.add(0, selected);
        } else {
            int tabIdx = playlistsPane.getSelectedIndex();
            if (tabIdx == -1) return;
            String playlistName = playlistsPane.getTitleAt(tabIdx);
            List<String> original = originalMusicNames.get(playlistName);
            if (original != null) names = new ArrayList<>(original);
            else {
                for (int i = 0; i < model.size(); i++) names.add(model.get(i));
            }
        }

        model.clear();
        for (String name : names) model.addElement(name);

        int tabIdx = playlistsPane.getSelectedIndex();
        if (tabIdx == -1) return;
        String playlistName = playlistsPane.getTitleAt(tabIdx);
        List<String> paths = new ArrayList<>();
        for (String name : names) paths.add(musicDirectory + playlistName + "/" + name + ".mp3");

        int newSel = currentMusicList.getSelectedIndex();
        if (newSel == -1 && !names.isEmpty()) newSel = 0;
        currentPlayer.reorderMusicPaths(paths.toArray(new String[0]), newSel);
        if (newSel != -1) currentMusicList.setSelectedIndex(newSel);
    }

    // ----- Entry points ----------------------------------------------

    public static void winMain() {
        Locale.setDefault(Locale.ROOT);
        SwingUtils.setDefaultFont("Microsoft Sans Serif", 18);
        EventQueue.invokeLater(() -> new Window().setVisible(true));
    }

    public static void main(String[] args) {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
        winMain();
    }
}