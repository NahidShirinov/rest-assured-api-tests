package az.apitest.unit;

import az.apitest.engine.DbChecker;
import az.apitest.model.ApiTestSuite;
import az.apitest.model.AwaitConfig;
import az.apitest.model.DataSource;
import az.apitest.model.DbCheck;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** DB yoxlamaları H2 in-memory bazası ilə (heç bir xarici baza lazım deyil). */
public class DbCheckerTest {

    private static final String URL = "jdbc:h2:mem:dbchecker;DB_CLOSE_DELAY=-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApiTestSuite suite = new ApiTestSuite();

    @BeforeClass
    public void createDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, status VARCHAR(20), amount DECIMAL(10,2), created_at TIMESTAMP)");
            st.execute("INSERT INTO orders VALUES ('o-1', 'CREATED', 12.50, TIMESTAMP '2026-10-02 10:00:00')");
            st.execute("INSERT INTO orders VALUES ('o-2', 'PAID', 99.00, TIMESTAMP '2026-10-02 11:00:00')");
        }
        DataSource ds = new DataSource();
        ds.url = URL;
        ds.user = "sa";
        ds.password = "";
        suite.datasources.put("shop", ds);
    }

    private static DbCheck check(String query, String expectJson, Object... params) throws Exception {
        DbCheck check = new DbCheck();
        check.datasource = "shop";
        check.query = query;
        check.params.addAll(java.util.List.of(params));
        MAPPER.readTree(expectJson).fields().forEachRemaining(e -> check.expect.put(e.getKey(), e.getValue()));
        return check;
    }

    @Test
    public void matchesRowsWithLowercaseColumnsAndIsoDates() throws Exception {
        Map<String, Object> vars = new HashMap<>(Map.of("orderId", "o-1"));
        DbCheck check = check("SELECT id, status, amount, created_at FROM orders WHERE id = ?",
                "{\"size()\": 1, \"[0].status\": \"CREATED\", \"[0].amount\": 12.5, \"[0].created_at\": \"startsWith:2026-10-02T10:00\"}",
                "${orderId}");
        DbChecker.check(check, suite, vars, vars);
    }

    @Test
    public void reportsEveryMismatchWithSqlAndResult() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        DbCheck check = check("SELECT status FROM orders WHERE id IN ('o-1', 'o-2') ORDER BY id", "{\"size()\": 3, \"[1].status\": \"CREATED\"}");
        AssertionError e = expectThrows(AssertionError.class, () -> DbChecker.check(check, suite, vars, vars));
        assertTrue(e.getMessage().startsWith("DB yoxlaması uğursuz oldu (2):"), e.getMessage());
        assertTrue(e.getMessage().contains("size(): gözlənilən 3, faktiki <2>"), e.getMessage());
        assertTrue(e.getMessage().contains("[1].status: gözlənilən \"CREATED\", faktiki <PAID>"), e.getMessage());
        assertTrue(e.getMessage().contains("SQL: SELECT status FROM orders WHERE id IN"), e.getMessage());
    }

    @Test
    public void extractsValuesIntoVariables() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        DbCheck check = check("SELECT status FROM orders WHERE id = ?", "{}", "o-2");
        check.extract.put("orderStatus", "[0].status");
        DbChecker.check(check, suite, vars, vars);
        assertEquals(vars.get("orderStatus"), "PAID");
    }

    @Test
    public void awaitWaitsForAsynchronousInsert() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        DbCheck check = check("SELECT status FROM orders WHERE id = ?", "{\"[0].status\": \"SHIPPED\"}", "o-async");
        check.await = new AwaitConfig();
        check.await.timeoutMs = 5000;
        check.await.intervalMs = 100;
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(400);
                try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement st = c.createStatement()) {
                    st.execute("INSERT INTO orders VALUES ('o-async', 'SHIPPED', 1, CURRENT_TIMESTAMP)");
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        writer.start();
        long start = System.currentTimeMillis();
        DbChecker.check(check, suite, vars, vars);
        assertTrue(System.currentTimeMillis() - start >= 300, "await gözləməli idi");
        writer.join();
    }

    @Test
    public void unknownDatasourceExplainsConfiguration() throws Exception {
        DbCheck check = check("SELECT 1", "{}");
        check.datasource = "billing";
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> DbChecker.check(check, suite, new HashMap<>(), new HashMap<>()));
        assertTrue(e.getMessage().contains("db.billing.url"), e.getMessage());
    }

    @Test
    public void sqlErrorShowsQuery() throws Exception {
        DbCheck check = check("SELECT nope FROM orders", "{}");
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> DbChecker.check(check, suite, new HashMap<>(), new HashMap<>()));
        assertTrue(e.getMessage().contains("SQL: SELECT nope FROM orders"), e.getMessage());
    }

    @SuppressWarnings("unused")
    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }
}
