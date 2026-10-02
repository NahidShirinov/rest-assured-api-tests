package az.apitest.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test fayllarını (bodyFile, dataFile, _globals.json, OpenAPI faylı) tapır.
 * Əvvəlcə birbaşa src/test/resources-dan (həmişə aktual, mvn clean lazım deyil), yoxdursa classpath-dan.
 */
public final class Resources {

    private Resources() {
    }

    /** src/test/resources/{relative} (Surefire "basedir"-i layihə kökünə qoyur; IDE-də iş qovluğu). */
    public static Path sourcePath(String relative) {
        return Path.of(System.getProperty("basedir", "."), "src", "test", "resources", relative);
    }

    public static boolean exists(String relative) {
        return Files.exists(sourcePath(relative))
                || Resources.class.getClassLoader().getResource(relative) != null;
    }

    public static String read(String relative) {
        Path source = sourcePath(relative);
        try {
            if (Files.isRegularFile(source)) {
                return Files.readString(source, StandardCharsets.UTF_8);
            }
            try (InputStream in = Resources.class.getClassLoader().getResourceAsStream(relative)) {
                if (in == null) {
                    throw new IllegalArgumentException("Fayl tapılmadı: " + relative
                            + " (axtarılan yer: " + source.toAbsolutePath() + " və classpath)");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Fayl oxunmadı: " + relative, e);
        }
    }
}
