package az.apitest.unit;

import az.apitest.engine.ApiTestExecutor;
import az.apitest.engine.DependencyFailedException;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import com.fasterxml.jackson.databind.node.TextNode;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Zəncirvari testlər: dəyişən çıxarmalı olan test uğursuz olanda və ya söndürüləndə
 * ondan asılı testlər "Naməlum dəyişən" yox, aydın DependencyFailedException almalıdır.
 * Şəbəkə lazım deyil: birinci test sorğu göndərilməzdən əvvəl uğursuz olur.
 */
public class DependencyChainTest {

    private static ApiTestCase testCase(String name, String path, Map<String, String> extract) {
        ApiTestCase tc = new ApiTestCase();
        tc.name = name;
        tc.path = path;
        tc.extract.putAll(extract);
        return tc;
    }

    @Test
    public void dependentTestIsSkippedWhenProducerFails() {
        ApiTestCase create = testCase("Create", "/items/${doesNotExist}", Map.of("itemId", "id"));
        ApiTestCase get = testCase("Get", "/items/${itemId}", Map.of());
        ApiTestCase getAgain = testCase("Get again", "/items/${itemId}/details", Map.of());
        ApiTestExecutor executor = new ApiTestExecutor(new ApiTestSuite());

        expectThrows(IllegalArgumentException.class, () -> executor.execute(create));

        DependencyFailedException e = expectThrows(DependencyFailedException.class, () -> executor.execute(get));
        assertTrue(e.getMessage().contains("${itemId}") && e.getMessage().contains("'Create' uğursuz oldu"),
                e.getMessage());
        // eyni dəyişəni istifadə edən sonrakı testlər də
        expectThrows(DependencyFailedException.class, () -> executor.execute(getAgain));
    }

    @Test
    public void skipCascadesThroughChain() {
        ApiTestCase first = testCase("First", "/a/${doesNotExist}", Map.of("a", "id"));
        ApiTestCase second = testCase("Second", "/b/${a}", Map.of("b", "id"));
        ApiTestCase third = testCase("Third", "/c/${b}", Map.of());
        ApiTestExecutor executor = new ApiTestExecutor(new ApiTestSuite());

        expectThrows(IllegalArgumentException.class, () -> executor.execute(first));
        expectThrows(DependencyFailedException.class, () -> executor.execute(second));

        DependencyFailedException e = expectThrows(DependencyFailedException.class, () -> executor.execute(third));
        assertTrue(e.getMessage().contains("'Second' keçildi"), e.getMessage());
    }

    @Test
    public void dependencyInExpectationsIsDetectedBeforeRequestIsSent() {
        ApiTestCase create = testCase("Create", "/items/${doesNotExist}", Map.of("itemId", "id"));
        // ${itemId} yalnız gözləntidədir, path-da yox
        ApiTestCase list = testCase("List", "/items", Map.of());
        list.expect.body.put("find { it.id == '${itemId}' }.name", TextNode.valueOf("notNull"));

        ApiTestSuite suite = new ApiTestSuite();
        suite.baseUrl = "http://localhost:1"; // heç nə işləmir: sorğu göndərilsə, ConnectException olar
        ApiTestExecutor executor = new ApiTestExecutor(suite);

        expectThrows(IllegalArgumentException.class, () -> executor.execute(create));
        expectThrows(DependencyFailedException.class, () -> executor.execute(list));
    }

    @Test
    public void dependentTestIsSkippedWhenProducerHasConnectionError() {
        // localhost:1-də heç nə işləmir -> dərhal "Connection refused" (checked ConnectException)
        ApiTestSuite suite = new ApiTestSuite();
        suite.baseUrl = "http://localhost:1";
        ApiTestExecutor executor = new ApiTestExecutor(suite);
        ApiTestCase create = testCase("Create", "/items", Map.of("itemId", "id"));
        ApiTestCase get = testCase("Get", "/items/${itemId}", Map.of());

        expectThrows(Exception.class, () -> executor.execute(create));

        DependencyFailedException e = expectThrows(DependencyFailedException.class, () -> executor.execute(get));
        assertTrue(e.getMessage().contains("'Create' uğursuz oldu"), e.getMessage());
    }

    @Test
    public void dependentTestIsSkippedWhenProducerIsDisabled() {
        ApiTestCase login = testCase("Login", "/login", Map.of("token", "token"));
        ApiTestCase me = testCase("Me", "/me?t=${token}", Map.of());
        ApiTestExecutor executor = new ApiTestExecutor(new ApiTestSuite());

        executor.skip(login);

        DependencyFailedException e = expectThrows(DependencyFailedException.class, () -> executor.execute(me));
        assertTrue(e.getMessage().contains("'Login' söndürülüb"), e.getMessage());
    }
}
