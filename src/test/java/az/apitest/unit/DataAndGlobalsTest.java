package az.apitest.unit;

import az.apitest.engine.SuiteLoader;
import az.apitest.model.ApiTestCase;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DataAndGlobalsTest {

    private static ApiTestCase testCase(String name) {
        ApiTestCase tc = new ApiTestCase();
        tc.name = name;
        tc.path = "/users";
        return tc;
    }

    @Test
    public void testWithoutDataStaysSingle() {
        ApiTestCase tc = testCase("t");
        assertEquals(SuiteLoader.expandData(tc), List.of(tc));
    }

    @Test
    public void dataSetsAndCsvRowsBecomeSeparateTests() {
        ApiTestCase tc = testCase("Yarat: ${case}");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("case", "inline");
        row.put("status", 201);
        tc.dataSets.add(row);
        tc.dataFile = "unit/users.csv";

        List<ApiTestCase> expanded = SuiteLoader.expandData(tc);

        assertEquals(expanded.size(), 4);
        assertEquals(expanded.get(0).name, "Yarat: inline");
        assertEquals(expanded.get(1).name, "Yarat: düzgün email");
        assertEquals(expanded.get(2).data.get("email"), "");
        assertEquals(expanded.get(2).data.get("status"), 400);
        assertTrue(expanded.get(3).dataSets.isEmpty() && expanded.get(3).dataFile == null);
    }

    @Test
    public void nameWithoutPlaceholderGetsRowNumber() {
        ApiTestCase tc = testCase("Yarat");
        tc.dataSets.add(Map.of("a", 1));
        tc.dataSets.add(Map.of("a", 2));
        List<ApiTestCase> expanded = SuiteLoader.expandData(tc);
        assertEquals(expanded.get(1).name, "Yarat [2]");
    }

}
