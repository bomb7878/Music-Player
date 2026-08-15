import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import java.io.*;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Core audio player that handles playback, pausing, seeking,
 * and repeat modes for a single playlist (list of MP3 files).
 * <p>
 * Uses JLayer for decoding and a custom {@link MusicAudioDevice} for volume control.
 * </p>
 */
public class MusicPlayer {
    private final List<String> musicPaths;
    private final long[] durations;          // track duration in milliseconds
    private final long[] id3TagSizes;        // size of ID3v2 header (if present)
    private final long[] fileSizes;          // total file size in bytes
    private MusicAudioDevice audioDevice;

    private Player player;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private volatile boolean isStoppedByUser = false;
    private int currentTrackIndex = -1;
    private int nextTrackIndex = -1;
    private int prevTrackIndex = -1;
    private int endTrackIndex;               // -1 = loop playlist, -2 = loop single track, otherwise normal end
    private long positionOffset = 0;         // absolute position offset (ms) when resuming
    private long playbackStartTime = 0;      // system time when playback started (for elapsed calculation)
    private int pausedPositionMs = 0;        // position where pause was triggered
    private float currentVolume = 1.0f;

    /**
     * Constructs a MusicPlayer for a list of MP3 file paths.
     * Pre‑reads duration, ID3 tag size and file size for each track.
     *
     * @param musicPaths full paths to MP3 files
     */
    public MusicPlayer(List<String> musicPaths) {
        this.musicPaths = musicPaths;
        this.durations = new long[musicPaths.size()];
        this.id3TagSizes = new long[musicPaths.size()];
        this.fileSizes = new long[musicPaths.size()];

        for (int i = 0; i < musicPaths.size(); i++) {
            durations[i] = getDuration(musicPaths.get(i));
            id3TagSizes[i] = getId3TagSize(musicPaths.get(i));
            fileSizes[i] = new File(musicPaths.get(i)).length();
        }

        this.endTrackIndex = musicPaths.size() - 1;
        this.audioDevice = new MusicAudioDevice();
    }

    // ----- File metadata helpers -------------------------------------

    private long getDuration(String filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            return audioFile.getAudioHeader().getTrackLength() * 1000L;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** Reads ID3v2 header size if present, otherwise returns 0. */
    private long getId3TagSize(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] header = new byte[10];
            if (fis.read(header) == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                return ((header[6] & 0x7F) << 21) |
                        ((header[7] & 0x7F) << 14) |
                        ((header[8] & 0x7F) << 7) |
                        (header[9] & 0x7F) + 10;
            }
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // ----- Internal player management --------------------------------

    /** Closes the current Player and thread without resetting state (used for pause). */
    private void closePlayerOnly() {
        if (player != null) {
            player.close();
            player = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        waitForThreadCompletion();
    }

    /** Full stop: closes player, resets all state and indexes. */
    private void closePlayerAndThread() {
        if (player != null) {
            player.close();
            player = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        waitForThreadCompletion();
        isPlaying = false;
        isPaused = false;
        setDefaultTrackIndexes();
        positionOffset = 0;
        playbackStartTime = 0;
        pausedPositionMs = 0;
    }

    /**
     * Starts playback of a specific track from a given byte offset and time position.
     * Creates a new Player and a fresh AudioDevice each time.
     *
     * @param index       track index
     * @param bytesToSkip absolute byte offset from the beginning of the file
     * @param newPosMs    corresponding time position (used as positionOffset)
     */
    private void startPlayer(int index, long bytesToSkip, long newPosMs) {
        if (index < 0 || index >= musicPaths.size()) {
            closePlayerAndThread();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(musicPaths.get(index));
            BufferedInputStream bis = new BufferedInputStream(fis);

            // Skip to the desired byte position
            long skipped = 0;
            while (skipped < bytesToSkip) {
                long result = bis.skip(bytesToSkip - skipped);
                if (result == 0) break;
                skipped += result;
            }

            // New device and player
            this.audioDevice = new MusicAudioDevice();
            player = new Player(bis, this.audioDevice);
            this.audioDevice.setVolume(currentVolume);

            currentTrackIndex = index;
            nextTrackIndex = index + 1;
            prevTrackIndex = index - 1;
            positionOffset = newPosMs;
            playbackStartTime = System.currentTimeMillis();
            isPlaying = true;
            isPaused = false;
            isStoppedByUser = false;

            // Playback thread
            playbackThread = new Thread(() -> {
                try {
                    player.play();
                } catch (JavaLayerException e) {
                    e.printStackTrace();
                } finally {
                    // When playback finishes (naturally or by interruption)
                    if (!isStoppedByUser) {
                        // Automatic transition based on repeat mode
                        int nextIndex = currentTrackIndex + 1;
                        if (endTrackIndex == musicPaths.size() - 1) {
                            if (nextIndex < musicPaths.size()) {
                                startPlayer(nextIndex, 0, 0);
                            } else {
                                closePlayerAndThread();
                            }
                        } else if (endTrackIndex == -1) {          // loop playlist
                            if (nextIndex >= musicPaths.size()) {
                                startPlayer(0, 0, 0);
                            }
                        } else if (endTrackIndex == -2) {          // loop single track
                            startPlayer(currentTrackIndex, 0, 0);
                        }
                    } else {
                        // User‑initiated stop
                        if (isPaused) {
                            // Leave player closed but keep state
                            if (player != null) {
                                player.close();
                                player = null;
                            }
                        } else {
                            closePlayerAndThread();
                        }
                    }
                }
            });
            playbackThread.setDaemon(true);
            playbackThread.start();

        } catch (IOException | JavaLayerException e) {
            e.printStackTrace();
        }
    }

    private void startPlayerFromBeginning(int index) {
        startPlayer(index, 0, 0);
    }

    private void waitForThreadCompletion() {
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1000);
            } catch (InterruptedException ignored) {}
            playbackThread = null;
        }
    }

    private void setDefaultTrackIndexes() {
        this.currentTrackIndex = -1;
        this.nextTrackIndex = -1;
        this.prevTrackIndex = -1;
    }

    // ----- Public API ------------------------------------------------

    /**
     * Reorders the internal music path list and associated metadata.
     * Used after shuffling the playlist to keep the player in sync.
     *
     * @param newPaths         new order of paths (must contain same files)
     * @param newCurrentIndex  new index to set as current (or -1 to reset)
     */
    public void reorderMusicPaths(String[] newPaths, int newCurrentIndex) {
        if (newPaths.length != musicPaths.size()) {
            throw new IllegalArgumentException("New array length must match current size");
        }

        String[] oldPaths = musicPaths.toArray(new String[0]).clone();
        long[] oldDurations = durations.clone();
        long[] oldId3 = id3TagSizes.clone();
        long[] oldSizes = fileSizes.clone();

        for (int i = 0; i < newPaths.length; i++) {
            int oldIndex = -1;
            for (int j = 0; j < oldPaths.length; j++) {
                if (oldPaths[j].equals(newPaths[i])) {
                    oldIndex = j;
                    break;
                }
            }
            if (oldIndex == -1) {
                throw new IllegalArgumentException("Path " + newPaths[i] + " not found in old list");
            }
            musicPaths.set(i, oldPaths[oldIndex]);
            durations[i] = oldDurations[oldIndex];
            id3TagSizes[i] = oldId3[oldIndex];
            fileSizes[i] = oldSizes[oldIndex];
        }

        if (newCurrentIndex >= 0 && newCurrentIndex < musicPaths.size()) {
            currentTrackIndex = newCurrentIndex;
        } else {
            setDefaultTrackIndexes();
        }
    }

    /** Play a track from the beginning. */
    public void playMusic(int index) {
        if (index < 0 || index >= musicPaths.size()) return;

        if (isPlaying) {
            isStoppedByUser = true;
            closePlayerAndThread();
        }
        startPlayerFromBeginning(index);
    }

    /** Stops playback completely. */
    public void stopMusic() {
        if (isPlaying) {
            isStoppedByUser = true;
            closePlayerAndThread();
        }
    }

    /** Toggle pause/resume. */
    public void togglePause() {
        if (!isPlaying && !isPaused) return;

        if (!isPaused) {
            // Pause
            pausedPositionMs = getCurrentPosition();
            positionOffset = pausedPositionMs;

            if (player != null) {
                isStoppedByUser = true;
                player.close();
                player = null;
            }
            if (playbackThread != null) {
                playbackThread.interrupt();
                playbackThread = null;
            }
            isPlaying = false;
            isPaused = true;
            isStoppedByUser = true;
            closePlayerOnly();
        } else {
            // Resume
            int total = (int) getTotalDuration();
            int pos = Math.max(0, Math.min(total, pausedPositionMs));

            long totalAudioBytes = fileSizes[currentTrackIndex] - id3TagSizes[currentTrackIndex];
            long bytesToSkip = (long) ((double) totalAudioBytes * pos / total);
            long absoluteBytes = bytesToSkip + id3TagSizes[currentTrackIndex];

            startPlayer(currentTrackIndex, absoluteBytes, pos);
            isPaused = false;
        }
    }

    /** Skip forward or backward by n seconds (negative = backward). */
    public void skipNSecond(int n) {
        if (!isPlaying || currentTrackIndex == -1) return;

        int total = (int) getTotalDuration();
        int position = getCurrentPosition();
        int newPos = Math.max(0, Math.min(total, position + n * 1000));

        long totalAudioBytes = fileSizes[currentTrackIndex] - id3TagSizes[currentTrackIndex];
        long bytesToSkip = (long) ((double) totalAudioBytes * newPos / total);

        if (player != null) {
            isStoppedByUser = true;
            player.close();
            player = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        startPlayer(currentTrackIndex, bytesToSkip + id3TagSizes[currentTrackIndex], newPos);
    }

    // ----- Repeat mode setters ---------------------------------------

    /** Enable playlist looping (repeat all). */
    public void setLoopingPlaylist() {
        this.endTrackIndex = -1;
    }

    /** Enable single‑track looping. */
    public void setLoopingOneTrack() {
        this.endTrackIndex = -2;
    }

    // ----- Volume control --------------------------------------------

    /** Set volume (0.0 – 1.0). */
    public void setVolume(float volume) {
        this.currentVolume = volume;
        if (audioDevice != null) {
            audioDevice.setVolume(volume);
        }
    }

    // ----- Getters ---------------------------------------------------

    public int size() {
        return musicPaths.size();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public long getTotalDuration() {
        return (currentTrackIndex == -1) ? 0 : durations[currentTrackIndex];
    }

    /**
     * Returns the current playback position in milliseconds.
     * Uses positionOffset + elapsed time when playing, otherwise positionOffset.
     */
    public int getCurrentPosition() {
        if (isPlaying) {
            return (int) (positionOffset + (System.currentTimeMillis() - playbackStartTime));
        } else {
            return (int) positionOffset;
        }
    }

    public int getEndTrackIndex() {
        return endTrackIndex;
    }

    public int getNextTrackIndex() {
        return nextTrackIndex;
    }

    public int getPrevTrackIndex() {
        return prevTrackIndex;
    }

    public MusicAudioDevice getAudioDevice() {
        return audioDevice;
    }

    public float getCurrentVolume() {
        return currentVolume;
    }
}