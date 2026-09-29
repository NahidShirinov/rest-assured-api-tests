package az.apitest.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Konfiqurasiya oxuyucusu.
 *
 * Prioritet (yuxarıdan aşağı):
 *   1. -Dkey=value           (mvn test -Dbase.url=https://...)
 *   2. ENV dəyişən KEY_NAME   (base.url -> BASE_URL)
 *   3. config/{env}.properties faylı (env = -Denv=dev|test|prod, default dev)
 */
public final class Config {

    private static final String ENV = System.getProperty("env", "dev");
    private static final Properties PROPS = load(ENV);

    private Config() {
    }

    private static Properties load(String env) {
        String file = "config/" + env + ".properties";
        Properties props = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("Config faylı tapılmadı: " + file);
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }

    public static String env() {
        return ENV;
    }

    public static String get(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        if (value == null) {
            value = PROPS.getProperty(key);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Config açarı tələb olunur: " + key + " (env=" + ENV + ")");
        }
        return value.trim();
    }

    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public static boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public static String baseUrl() {
        return require("base.url");
    }
}
