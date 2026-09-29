package az.apitest.tests.datadriven;

import az.apitest.engine.ApiTestExecutor;
import az.apitest.engine.DependencyFailedException;
import az.apitest.engine.SuiteLoader;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * testdata/*.json fayllarındakı BÜTÜN testləri işlədir.
 * Yeni API testi üçün Java yazmağa ehtiyac yoxdur - sadəcə JSON əlavə et.
 */
public class JsonDrivenApiTest {

    @DataProvider(name = "apiCases")
    public Object[][] apiCases() {
        List<Object[]> rows = new ArrayList<>();
        for (ApiTestSuite suite : SuiteLoader.loadAll()) {
            ApiTestExecutor executor = new ApiTestExecutor(suite);
            for (ApiTestCase tc : suite.tests) {
                rows.add(new Object[]{suite.suite, tc, executor});
            }
        }
        return rows.toArray(new Object[0][]);
    }

    /** Hesabatda hər test öz adı ilə görünsün: "[Posts CRUD] POST /posts - Post yarat" */
    @BeforeMethod
    public void setName(ITestResult result, Object[] params) {
        if (params.length >= 2) {
            result.setTestName("[" + params[0] + "] " + params[1]);
        }
    }

    @Test(dataProvider = "apiCases")
    public void run(String suiteName, ApiTestCase tc, ApiTestExecutor executor) {
        if (!tc.enabled) {
            executor.skip(tc);
            throw new SkipException("disabled: " + tc.name);
        }
        try {
            executor.execute(tc);
        } catch (DependencyFailedException e) {
            throw new SkipException(e.getMessage());
        }
    }
}
