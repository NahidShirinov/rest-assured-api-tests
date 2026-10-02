package az.apitest.unit;

import az.apitest.engine.ApiTestExecutor;
import az.apitest.engine.SuiteLoader;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import az.apitest.model.AwaitConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Yeni imkanlar mühərrik (ApiTestExecutor) üzərindən, lokal HTTP serverə qarşı:
 * OpenAPI yoxlaması, bodyFile + overrides/remove, dataSets + "${status}", await, db.
 */
public class EngineFeaturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private LocalApi api;

    @BeforeClass
    public void start() throws IOException {
        api = new LocalApi();
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        api.close();
    }

    private ApiTestSuite suite(String json) throws IOException {
        ApiTestSuite suite = MAPPER.readValue(json, ApiTestSuite.class);
        suite.baseUrl = api.baseUrl();
        List<ApiTestCase> expanded = new java.util.ArrayList<>();
        suite.tests.forEach(tc -> expanded.addAll(SuiteLoader.expandData(tc)));
        suite.tests = expanded;
        return suite;
    }

    @Test
    public void openApiValidResponsePasses() throws IOException {
        ApiTestSuite suite = suite("{\"openapi\": true, \"openapiSpec\": \"unit/users-openapi.yaml\", \"tests\": ["
                + "{\"name\": \"get\", \"path\": \"/users/1\", \"expect\": {\"status\": 200}}]}");
        new ApiTestExecutor(suite).execute(suite.tests.get(0));
    }

    @Test
    public void openApiDetectsContractViolation() throws IOException {
        ApiTestSuite suite = suite("{\"openapi\": true, \"openapiSpec\": \"unit/users-openapi.yaml\", \"tests\": ["
                + "{\"name\": \"get\", \"path\": \"/users/2\", \"expect\": {\"status\": 200}}]}");
        AssertionError e = expectThrows(AssertionError.class, () -> new ApiTestExecutor(suite).execute(suite.tests.get(0)));
        assertTrue(e.getMessage().startsWith("Cavab OpenAPI spesifikasiyasına uyğun deyil (unit/users-openapi.yaml):"), e.getMessage());
        assertTrue(e.getMessage().contains("integer"), e.getMessage());
        assertFalse(e.getMessage().contains("[IGNORE]"), e.getMessage());
    }

    @Test
    public void openApiCanBeDisabledPerTestAndIgnoresUndocumentedStatus() throws IOException {
        ApiTestSuite suite = suite("{\"openapi\": true, \"openapiSpec\": \"unit/users-openapi.yaml\", \"tests\": ["
                + "{\"name\": \"bad but off\", \"path\": \"/users/2\", \"expect\": {\"status\": 200, \"openapi\": false}},"
                + "{\"name\": \"400 not in spec\", \"method\": \"POST\", \"path\": \"/users\", \"body\": {}, \"expect\": {\"status\": 400}}]}");
        ApiTestExecutor executor = new ApiTestExecutor(suite);
        executor.execute(suite.tests.get(0));
        executor.execute(suite.tests.get(1));
    }

    @Test
    public void bodyFileWithOverridesIsResolvedAndSent() throws IOException {
        ApiTestSuite suite = suite("{\"variables\": {\"role\": \"ADMIN\"}, \"tests\": [{\"name\": \"create\", \"method\": \"POST\","
                + "\"path\": \"/users\", \"bodyFile\": \"unit/user-body.json\","
                + "\"bodyOverrides\": {\"role\": \"${role}\", \"address.city\": \"Ganja\"},"
                + "\"expect\": {\"status\": 201, \"body\": {\"role\": \"ADMIN\", \"address.city\": \"Ganja\", \"email\": \"endsWith:@test.com\"}}}]}");
        new ApiTestExecutor(suite).execute(suite.tests.get(0));
        assertTrue(api.lastUserBody.get("email").asText().matches("user_\\w+@test\\.com"), api.lastUserBody.toString());
    }

    @Test
    public void bodyRemoveProducesNegativeCase() throws IOException {
        ApiTestSuite suite = suite("{\"tests\": [{\"name\": \"no email\", \"method\": \"POST\", \"path\": \"/users\","
                + "\"bodyFile\": \"unit/user-body.json\", \"bodyRemove\": [\"email\"], \"expect\": {\"status\": 400}}]}");
        new ApiTestExecutor(suite).execute(suite.tests.get(0));
        assertFalse(api.lastUserBody.has("email"));
    }

    @Test
    public void dataSetsRunEachRowWithItsOwnExpectedStatus() throws IOException {
        ApiTestSuite suite = suite("{\"tests\": [{\"name\": \"create: ${case}\", \"method\": \"POST\", \"path\": \"/users\","
                + "\"body\": {\"name\": \"Ali\", \"email\": \"${email}\"}, \"expect\": {\"status\": \"${status}\"},"
                + "\"dataFile\": \"unit/users.csv\"}]}");
        assertEquals(suite.tests.size(), 3);
        ApiTestExecutor executor = new ApiTestExecutor(suite);
        for (ApiTestCase tc : suite.tests) {
            executor.execute(tc);
        }
        assertEquals(api.lastUserBody.get("email").asText(), "a@b.com");
    }

    @Test
    public void awaitRetriesRequestUntilConditionHolds() throws IOException {
        ApiTestSuite suite = suite("{\"tests\": [{\"name\": \"job\", \"path\": \"/jobs/7\","
                + "\"await\": {\"timeoutMs\": 3000, \"intervalMs\": 50},"
                + "\"expect\": {\"status\": 200, \"body\": {\"status\": \"DONE\", \"calls\": 3}}}]}");
        new ApiTestExecutor(suite).execute(suite.tests.get(0));
    }

    @Test
    public void withoutAwaitTheSameCheckFails() throws IOException {
        ApiTestSuite suite = suite("{\"tests\": [{\"name\": \"job\", \"path\": \"/jobs/8\","
                + "\"expect\": {\"status\": 200, \"body\": {\"status\": \"DONE\"}}}]}");
        expectThrows(AssertionError.class, () -> new ApiTestExecutor(suite).execute(suite.tests.get(0)));
    }

    @Test
    public void dbCheckRunsAfterApiAndSeesExtractedVariables() throws Exception {
        String url = "jdbc:h2:mem:engine;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id INT, name VARCHAR(50))");
            st.execute("INSERT INTO users VALUES (1, 'Ali')");
        }
        ApiTestSuite suite = suite("{\"datasources\": {\"app\": {\"url\": \"" + url + "\", \"user\": \"sa\", \"password\": \"\"}},"
                + "\"tests\": [{\"name\": \"get + db\", \"path\": \"/users/1\", \"extract\": {\"userId\": \"id\"},"
                + "\"expect\": {\"status\": 200},"
                + "\"db\": {\"datasource\": \"app\", \"query\": \"SELECT name FROM users WHERE id = ?\", \"params\": [\"${userId}\"],"
                + "\"expect\": {\"size()\": 1, \"[0].name\": \"Ali\"}, \"extract\": {\"dbName\": \"[0].name\"}}}]}");
        ApiTestExecutor executor = new ApiTestExecutor(suite);
        executor.execute(suite.tests.get(0));
        assertEquals(executor.variables().get("dbName"), "Ali");
    }

    @Test
    public void failingDbCheckFailsTheTest() throws Exception {
        String url = "jdbc:h2:mem:engine2;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id INT)");
        }
        ApiTestSuite suite = suite("{\"datasources\": {\"app\": {\"url\": \"" + url + "\", \"user\": \"sa\"}},"
                + "\"tests\": [{\"name\": \"t\", \"path\": \"/users/1\", \"expect\": {\"status\": 200},"
                + "\"db\": [{\"datasource\": \"app\", \"query\": \"SELECT * FROM users\", \"expect\": {\"size()\": 1}}]}]}");
        AssertionError e = expectThrows(AssertionError.class, () -> new ApiTestExecutor(suite).execute(suite.tests.get(0)));
        assertTrue(e.getMessage().startsWith("DB yoxlaması uğursuz oldu"), e.getMessage());
    }

    @SuppressWarnings("unused")
    private static AwaitConfig unused() {
        return null;
    }
}
