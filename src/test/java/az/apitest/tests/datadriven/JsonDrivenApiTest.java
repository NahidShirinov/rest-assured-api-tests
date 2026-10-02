package az.apitest.tests.datadriven;

import az.apitest.engine.ApiTestExecutor;
import az.apitest.engine.DependencyFailedException;
import az.apitest.engine.SuiteLoader;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Parameter;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        describeForAllure(suiteName, tc);
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

    /** Allure hesabatında: test adı, suite qrupu, tag-lər, data sətri parametr kimi; tarixçə üçün sabit id. */
    private static void describeForAllure(String suiteName, ApiTestCase tc) {
        String id = UUID.nameUUIDFromBytes((suiteName + "::" + tc.name).getBytes(StandardCharsets.UTF_8)).toString();
        Allure.getLifecycle().updateTestCase(result -> {
            result.setName(tc.name);
            result.setFullName(suiteName + " :: " + tc.name);
            result.setHistoryId(id);
            result.setTestCaseId(id);
            result.setDescription((tc.description == null ? "" : tc.description + "\n\n") + tc.method + " " + tc.path);
            List<Parameter> parameters = new ArrayList<>();
            tc.data.forEach((k, v) -> parameters.add(new Parameter().setName(k).setValue(String.valueOf(v))));
            result.setParameters(parameters);
            // ağac: TestNG suite -> JSON suite adı -> test (Java sinif adı səviyyəsi lazımsızdır)
            result.getLabels().removeIf(label -> "suite".equals(label.getName()) || "subSuite".equals(label.getName()));
            result.getLabels().add(new Label().setName("suite").setValue(suiteName));
            tc.tags.forEach(tag -> result.getLabels().add(new Label().setName("tag").setValue(tag)));
        });
    }
}
