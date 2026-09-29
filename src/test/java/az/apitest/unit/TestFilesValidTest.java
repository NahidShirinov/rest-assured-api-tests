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

    @DataProvider
    public Object[][] files() throws IOException {
        try (Stream<Path> stream = Stream.of("testdata", "examples")
                .map(RESOURCES::resolve)
                .filter(Files::isDirectory)
                .flatMap(TestFilesValidTest::walk)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .map(p -> new Object[]{RESOURCES.relativize(p).toString(), p})
                    .toArray(Object[][]::new);
        }
    }

    @Test(dataProvider = "files")
    public void matchesTestFileSchema(String name, Path file) throws IOException {
        assertThat(name, Files.readString(file), matchesJsonSchemaInClasspath("api-test.schema.json"));
    }

    @Test(dataProvider = "files")
    public void isReadableByFramework(String name, Path file) {
        SuiteLoader.read(file);
    }

    private static Stream<Path> walk(Path dir) {
        try {
            return Files.walk(dir);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
