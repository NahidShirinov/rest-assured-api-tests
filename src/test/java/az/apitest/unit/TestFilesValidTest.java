package az.apitest.unit;

import az.apitest.engine.SuiteLoader;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Bütün test JSON fayllarını API-yə getmədən yoxlayır: formata (api-test.schema.json) uyğundurmu
 * və framework onları oxuya bilirmi. Yazı səhvi ("expcet", "methd": "GTE") burada tutulur.
 */
public class TestFilesValidTest {

    private static final Path RESOURCES = Path.of(System.getProperty("basedir", "."), "src", "test", "resources");

    /** Suite faylları: "_" ilə başlayan fayl/qovluqlar (_globals.json, _files/, generator qaralamaları) suite deyil. */
    @DataProvider
    public Object[][] files() throws IOException {
        return jsonFiles(rel -> !SuiteLoader.hasUnderscoreSegment(rel));
    }

    @DataProvider
    public Object[][] globalsFiles() throws IOException {
        return jsonFiles(rel -> rel.toString().equals("_globals.json"));
    }

    private static Object[][] jsonFiles(java.util.function.Predicate<Path> relativeFilter) {
        return Stream.of("testdata", "examples")
                .map(RESOURCES::resolve)
                .filter(Files::isDirectory)
                .flatMap(root -> walk(root)
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> relativeFilter.test(root.relativize(p))))
                .sorted()
                .map(p -> new Object[]{RESOURCES.relativize(p).toString(), p})
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "files")
    public void matchesTestFileSchema(String name, Path file) throws IOException {
        assertThat(name, Files.readString(file), matchesJsonSchemaInClasspath("api-test.schema.json"));
    }

    @Test(dataProvider = "files")
    public void isReadableByFramework(String name, Path file) {
        SuiteLoader.read(file);
    }

    @Test(dataProvider = "globalsFiles")
    public void globalsMatchSchema(String name, Path file) throws IOException {
        assertThat(name, Files.readString(file), matchesJsonSchemaInClasspath("api-test-globals.schema.json"));
        SuiteLoader.readGlobals(file);
    }

    private static Stream<Path> walk(Path dir) {
        try {
            return Files.walk(dir);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
