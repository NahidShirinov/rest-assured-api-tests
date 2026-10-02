package az.apitest.engine;

import az.apitest.config.Config;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * testdata/ qovluğundan bütün *.json suite-ləri yükləyir.
 *
 * Fayllar birbaşa src/test/resources/{dir} qovluğundan oxunur, target/-dakı kopyadan yox.
 * Beləcə silinmiş/adı dəyişmiş fayllar dərhal yox olur (mvn clean lazım deyil)
 * və IDE-dən işlədəndə də eyni nəticə alınır. Mənbə qovluq tapılmasa classpath-a baxılır.
 *
 * Filtrlər:
 *   -Dsuite=posts         yalnız adında "posts" olan fayllar (vergüllə bir neçə)
 *   -Dtags=smoke,crud     yalnız bu tag-lardan biri olan testlər
 *   -Dtestdata.dir=...    başqa qovluq (default: testdata)
 *
 * dataSets / dataFile olan test hər data sətri üçün ayrıca testə çevrilir.
 */
public final class SuiteLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SuiteLoader() {
    }

    public static List<ApiTestSuite> loadAll() {
        String dir = Config.get("testdata.dir", "testdata");
        Set<String> suiteFilter = csv(Config.get("suite", ""));
        Set<String> tagFilter = csv(Config.get("tags", ""));

        List<ApiTestSuite> suites = new ArrayList<>();
        for (Path file : listJsonFiles(dir)) {
            String fileName = file.getFileName().toString();
            if (!suiteFilter.isEmpty() && suiteFilter.stream().noneMatch(fileName::contains)) {
                continue;
            }
            ApiTestSuite suite = read(file);
            if (!tagFilter.isEmpty()) {
                suite.tests.removeIf(tc -> tc.tags.stream().noneMatch(tagFilter::contains));
            }
            if (!suite.tests.isEmpty()) {
                suites.add(suite);
            }
        }
        return suites;
    }

    public static ApiTestSuite read(Path file) {
        try {
            ApiTestSuite suite = MAPPER.readValue(file.toFile(), ApiTestSuite.class);
            suite.sourceFile = file.getFileName().toString();
            if (suite.suite == null) {
                suite.suite = suite.sourceFile.replace(".json", "");
            }
            List<ApiTestCase> expanded = new ArrayList<>();
            for (int i = 0; i < suite.tests.size(); i++) {
                ApiTestCase tc = suite.tests.get(i);
                if (tc.path == null) {
                    throw new IllegalStateException(suite.sourceFile + " -> test #" + (i + 1) + ": 'path' boşdur");
                }
                if (tc.name == null) {
                    tc.name = tc.method + " " + tc.path;
                }
                expanded.addAll(expandData(tc));
            }
            suite.tests = expanded;
            return suite;
        } catch (IOException e) {
            throw new UncheckedIOException("JSON oxunmadı: " + file + " -> " + e.getMessage(), e);
        }
    }

    /** dataSets + dataFile sətirləri -> hər biri ayrıca test (data = sətrin dəyərləri). */
    public static List<ApiTestCase> expandData(ApiTestCase tc) {
        List<Map<String, Object>> rows = new ArrayList<>(tc.dataSets);
        if (tc.dataFile != null) {
            rows.addAll(Csv.parse(Resources.read(tc.dataFile)));
        }
        if (rows.isEmpty()) {
            return List.of(tc);
        }
        List<ApiTestCase> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            ApiTestCase copy = MAPPER.convertValue(tc, ApiTestCase.class);
            copy.dataSets = new ArrayList<>();
            copy.dataFile = null;
            copy.data = new LinkedHashMap<>(row);
            String name = tc.name;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                name = name.replace("${" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
            copy.name = name.equals(tc.name) ? tc.name + " [" + (i + 1) + "]" : name;
            result.add(copy);
        }
        return result;
    }

    private static List<Path> listJsonFiles(String dir) {
        return listJsonFiles(resolveDir(dir));
    }

    private static Path resolveDir(String dir) {
        // Surefire "basedir"-i layihə kökünə qoyur; IDE-də isə iş qovluğu adətən layihə köküdür
        Path source = Path.of(System.getProperty("basedir", "."), "src", "test", "resources", dir);
        if (Files.isDirectory(source)) {
            return source;
        }
        URL url = SuiteLoader.class.getClassLoader().getResource(dir);
        if (url == null) {
            throw new IllegalStateException("testdata qovluğu tapılmadı: " + source.toAbsolutePath());
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Path> listJsonFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
