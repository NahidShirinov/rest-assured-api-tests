package az.apitest.engine;

import az.apitest.config.Config;
import az.apitest.matchers.MatcherFactory;
import az.apitest.model.ApiTestSuite;
import az.apitest.model.DataSource;
import az.apitest.model.DbCheck;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.qameta.allure.Allure;
import io.restassured.path.json.JsonPath;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * "db" yoxlamalarını icra edir: SQL -> sətirlər (List<Map>) -> GPath + matcher-lər.
 *
 * Bağlantı (prioritet): suite/_globals.json "datasources" -> config db.<ad>.url / db.url (+ user, password).
 * Sütun adları default kiçik hərflə (db.lowercaseColumns=false ilə söndürmək olar), çünki bazalar fərqlidir
 * (Postgres: status, H2/Oracle: STATUS).
 */
public final class DbChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private DbChecker() {
    }

    /** Yoxlamanı icra edir (await varsa təkrarlayır) və extract dəyərlərini vars-a yazır. */
    public static void check(DbCheck check, ApiTestSuite suite, Map<String, Object> scope, Map<String, Object> vars) {
        if (check.query == null || check.query.isBlank()) {
            throw new IllegalArgumentException("db: 'query' boşdur");
        }
        String sql = Placeholders.resolve(check.query, scope);
        List<Object> params = new ArrayList<>();
        check.params.forEach(p -> params.add(Placeholders.resolveValue(p, scope)));
        Map<String, Matcher<?>> matchers = new LinkedHashMap<>();
        check.expect.forEach((path, expected) ->
                matchers.put(Placeholders.resolve(path, scope), MatcherFactory.from(Placeholders.resolve(expected, scope))));
        ConnectionInfo connection = connection(check.datasource, suite, scope);

        JsonPath result = Await.until(check.await, () -> {
            List<Map<String, Object>> rows = query(connection, sql, params);
            String json = toJson(rows);
            Allure.addAttachment("DB: " + check.datasource, "text/plain",
                    sql + "\nparams: " + params + "\n\n" + json);
            JsonPath jsonPath = new JsonPath(json);
            assertRows(sql, params, json, jsonPath, matchers);
            return jsonPath;
        });

        check.extract.forEach((name, path) -> {
            Object value = result.get(path);
            if (value == null) {
                throw new AssertionError("db extract '" + name + "' üçün dəyər tapılmadı: " + path);
            }
            vars.put(name, value);
        });
    }

    record ConnectionInfo(String name, String url, String user, String password) {
    }

    static ConnectionInfo connection(String name, ApiTestSuite suite, Map<String, Object> scope) {
        DataSource ds = suite.datasources.get(name);
        if (ds != null) {
            return new ConnectionInfo(name, Placeholders.resolve(ds.url, scope),
                    Placeholders.resolve(ds.user, scope), Placeholders.resolve(ds.password, scope));
        }
        String prefix = "default".equals(name) ? "db." : "db." + name + ".";
        String url = Config.get(prefix + "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Baza bağlantısı tapılmadı: '" + name + "'. config-ə " + prefix
                    + "url (+ " + prefix + "user, " + prefix + "password) yaz və ya suite-də \"datasources\" təyin et");
        }
        return new ConnectionInfo(name, url, Config.get(prefix + "user"), Config.get(prefix + "password"));
    }

    static List<Map<String, Object>> query(ConnectionInfo info, String sql, List<Object> params) {
        boolean lowercase = Config.getBool("db.lowercaseColumns", true);
        try (Connection c = DriverManager.getConnection(info.url(), info.user(), info.password());
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String column = md.getColumnLabel(i);
                        row.put(lowercase ? column.toLowerCase(Locale.ROOT) : column, value(rs.getObject(i)));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("DB sorğusu uğursuz oldu (" + info.name() + ", " + info.url() + "): "
                    + e.getMessage() + "\nSQL: " + sql + "\nparams: " + params, e);
        }
    }

    /** JDBC tiplərini JSON-a uyğun dəyərə çevirir (tarixlər ISO mətn kimi). */
    private static Object value(Object v) throws SQLException {
        if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) return v;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof java.sql.Time t) return t.toLocalTime().toString();
        if (v instanceof TemporalAccessor || v instanceof UUID) return v.toString();
        if (v instanceof Clob clob) return clob.getSubString(1, (int) clob.length());
        if (v instanceof byte[] bytes) return "<binary " + bytes.length + " bytes>";
        return v.toString();
    }

    private static void assertRows(String sql, List<Object> params, String json, JsonPath jsonPath,
                                   Map<String, Matcher<?>> matchers) {
        List<String> failures = new ArrayList<>();
        matchers.forEach((path, matcher) -> {
            Object actual;
            try {
                actual = jsonPath.get(path);
            } catch (RuntimeException e) {
                failures.add(path + ": GPath xətası - " + e.getMessage());
                return;
            }
            if (!matcher.matches(actual)) {
                failures.add(path + ": gözlənilən " + StringDescription.toString(matcher) + ", faktiki <" + actual + ">");
            }
        });
        if (!failures.isEmpty()) {
            throw new AssertionError("DB yoxlaması uğursuz oldu (" + failures.size() + "):\n  "
                    + String.join("\n  ", failures)
                    + "\nSQL: " + sql + "\nparams: " + params + "\nnəticə: " + abbreviate(json));
        }
    }

    private static String toJson(List<Map<String, Object>> rows) {
        try {
            return MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) + "\n... (" + s.length() + " simvol)" : s;
    }
}
