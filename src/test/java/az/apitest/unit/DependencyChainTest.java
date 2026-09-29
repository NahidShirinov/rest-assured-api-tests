package az.apitest.unit;

import az.apitest.engine.ApiTestExecutor;
import az.apitest.engine.DependencyFailedException;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
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
    public void dependentTestIsSkippedWhenProducerIsDisabled() {
        ApiTestCase login = testCase("Login", "/login", Map.of("token", "token"));
        ApiTestCase me = testCase("Me", "/me?t=${token}", Map.of());
        ApiTestExecutor executor = new ApiTestExecutor(new ApiTestSuite());

        executor.skip(login);

        DependencyFailedException e = expectThrows(DependencyFailedException.class, () -> executor.execute(me));
        assertTrue(e.getMessage().contains("'Login' söndürülüb"), e.getMessage());
    }
}
