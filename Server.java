import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

/**
 * Utility class responsible for extracting all ZIP archives from the "archives/"
 * subdirectory into separate folders inside the main music directory.
 * <p>
 * Each archive becomes a playlist folder with the same name (without .zip).
 * </p>
 */
public class Server {
    private static final String MUSIC_DIR = "./music/";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Extracts all .zip files found in the "archives/" folder.
     * For each archive, a subfolder is created in the main music directory
     * and all MP3 files from the archive are extracted into it.
     * Existing files are not overwritten (if the target already exists, it is skipped).
     *
     * @throws IllegalArgumentException if the archives folder does not exist
     */
    public static void genMusicDirectories() {
        File folder = new File(MUSIC_DIR + "archives/");
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Archives folder does not exist: " + folder.getAbsolutePath());
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        if (files == null || files.length == 0) {
            return;
        }

        for (File zipFile : files) {
            String playlistName = zipFile.getName().replace(".zip", "");
            File playlistFolder = new File(MUSIC_DIR + playlistName);
            if (!playlistFolder.exists()) {
                playlistFolder.mkdirs();
            }

            try (ZipFile zip = new ZipFile(zipFile, CHARSET.name())) {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    String fileName = new File(entry.getName()).getName();   // strip directory parts
                    File target = new File(playlistFolder, fileName);

                    if (!target.exists()) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read archive: " + zipFile.getName() + " - " + e.getMessage());
            }
        }
    }
}