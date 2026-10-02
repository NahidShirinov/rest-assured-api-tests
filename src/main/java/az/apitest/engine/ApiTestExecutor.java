package az.apitest.engine;

import az.apitest.config.Config;
import az.apitest.core.SpecFactory;
import az.apitest.matchers.MatcherFactory;
import az.apitest.model.ApiTestCase;
import az.apitest.model.ApiTestSuite;
import az.apitest.model.DbCheck;
import az.apitest.model.Expectation;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.hamcrest.Matcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Framework-ün mühərriki: ApiTestCase-i götürür, sorğunu göndərir,
 * bütün gözləntiləri bir dəfəyə yoxlayır və dəyişənləri çıxarır.
 */
public final class ApiTestExecutor {

    private final ApiTestSuite suite;
    /** Suite boyunca paylaşılan dəyişənlər (variables + extract). */
    private final Map<String, Object> vars = new LinkedHashMap<>();

    public ApiTestExecutor(ApiTestSuite suite) {
        this.suite = suite;
        suite.variables.forEach((k, v) -> vars.put(k, Placeholders.resolveValue(v, vars)));
    }

    public Map<String, Object> variables() {
        return vars;
    }

    @Override
    public String toString() {
        return "vars=" + vars.keySet();
    }

    public Response execute(ApiTestCase tc) {
        try {
            // Gözləntilər və body sorğudan ƏVVƏL qurulur: içindəki ${...} asılılıqları da
            // sorğu göndərilməzdən öncə yoxlanır (yoxdursa, test sorğusuz skip olur)
            Map<String, Object> scope = scope(tc);
            ResponseSpecification expectations = buildExpectations(tc.expect, scope);
            JsonNode template = BodyBuilder.build(tc);
            JsonNode body = template == null ? null : Placeholders.resolve(template, scope);
            String openApiSpec = openApiSpec(tc, scope);

            Response response = Await.until(tc.await, () -> {
                AtomicReference<ValidationReport> report = new AtomicReference<>();
                Response r = send(tc, scope, body, openApiSpec == null ? null : report, openApiSpec);
                r.then().spec(expectations);
                if (openApiSpec != null) {
                    OpenApiValidation.assertValid(openApiSpec, report.get());
                }
                return r;
            });
            extract(tc, response);
            for (DbCheck check : tc.db) {
                DbChecker.check(check, suite, scope(tc), vars);
            }
            return response;
        } catch (DependencyFailedException e) {
            markUnavailable(tc, "keçildi (asılı olduğu test uğursuz oldu)");
            throw e;
        } catch (Throwable e) {
            // Throwable: REST Assured şəbəkə xətalarını (ConnectException və s.) checked olsa da
            // Groovy vasitəsilə elan etmədən atır - onları da tutmaq lazımdır
            markUnavailable(tc, "uğursuz oldu");
            throw e;
        }
    }

    /** Söndürülmüş test: onun çıxarmalı olduğu dəyişənlər də yoxdur. */
    public void skip(ApiTestCase tc) {
        markUnavailable(tc, "söndürülüb (enabled=false)");
    }

    private void markUnavailable(ApiTestCase tc, String reason) {
        tc.extract.keySet().forEach(name ->
                vars.put(name, new DependencyFailedException.Unavailable(tc.name, reason)));
    }

    /** Bu testin dəyişənləri: suite dəyişənləri + (varsa) data sətrinin dəyərləri. */
    private Map<String, Object> scope(ApiTestCase tc) {
        if (tc.data.isEmpty()) {
            return vars;
        }
        Map<String, Object> scope = new LinkedHashMap<>(vars);
        scope.putAll(tc.data);
        return scope;
    }

    /** OpenAPI yoxlaması aktivdirsə spesifikasiya yeri, deyilsə null. */
    private String openApiSpec(ApiTestCase tc, Map<String, Object> scope) {
        boolean enabled = tc.expect.openapi != null ? tc.expect.openapi : Boolean.TRUE.equals(suite.openapi);
        if (!enabled) {
            return null;
        }
        String spec = suite.openapiSpec != null ? suite.openapiSpec : Config.get("openapi.spec");
        if (spec == null || spec.isBlank()) {
            throw new IllegalStateException("'" + tc.name + "': \"openapi\": true üçün spesifikasiya lazımdır - "
                    + "config-ə openapi.spec=<URL və ya fayl> yaz və ya suite-də \"openapiSpec\" təyin et");
        }
        return Placeholders.resolve(spec, scope);
    }

    private Response send(ApiTestCase tc, Map<String, Object> scope, JsonNode body,
                          AtomicReference<ValidationReport> openApiReport, String openApiSpec) {
        String baseUrl = suite.baseUrl != null ? Placeholders.resolve(suite.baseUrl, scope) : Config.baseUrl();
        RequestSpecification req = given().spec(SpecFactory.create(baseUrl)).filter(new AllureRestAssured());
        if (openApiReport != null) {
            req.filter(OpenApiValidation.filter(openApiSpec, openApiReport));
        }

        suite.headers.forEach((k, v) -> req.header(k, Placeholders.resolve(v, scope)));
        tc.headers.forEach((k, v) -> req.header(k, Placeholders.resolve(v, scope)));
        tc.queryParams.forEach((k, v) -> req.queryParam(k, Placeholders.resolveValue(v, scope)));
        tc.pathParams.forEach((k, v) -> req.pathParam(k, Placeholders.resolveValue(v, scope)));

        if (!tc.formParams.isEmpty()) {
            req.contentType("application/x-www-form-urlencoded");
            tc.formParams.forEach((k, v) -> req.formParam(k, Placeholders.resolveValue(v, scope)));
        }
        if (body != null) {
            req.body(body.toString());
        }

        String path = Placeholders.resolve(tc.path, scope);
        return req.request(Method.valueOf(tc.method.toUpperCase()), path);
    }

    @SuppressWarnings("unchecked")
    private ResponseSpecification buildExpectations(Expectation exp, Map<String, Object> scope) {
        ResponseSpecBuilder spec = new ResponseSpecBuilder();

        if (exp.status != null) {
            Object status = Placeholders.resolveValue(exp.status, scope);
            spec.expectStatusCode(status instanceof Number n ? n.intValue() : Integer.parseInt(status.toString().trim()));
        }
        if (exp.maxTimeMs != null) {
            spec.expectResponseTime(lessThanOrEqualTo(exp.maxTimeMs), TimeUnit.MILLISECONDS);
        }
        if (exp.schema != null) {
            spec.expectBody(matchesJsonSchemaInClasspath(Placeholders.resolve(exp.schema, scope)));
        }
        exp.headers.forEach((name, value) ->
                spec.expectHeader(name, (Matcher<String>) MatcherFactory.fromString(Placeholders.resolve(value, scope))));
        exp.body.forEach((path, expected) -> {
            JsonNode resolved = Placeholders.resolve(expected, scope);
            spec.expectBody(Placeholders.resolve(path, scope), MatcherFactory.from(resolved));
        });
        return spec.build();
    }

    private void extract(ApiTestCase tc, Response response) {
        tc.extract.forEach((name, source) -> {
            Object value;
            if (source.startsWith("header:")) {
                value = response.header(source.substring("header:".length()));
            } else if (source.equals("status")) {
                value = response.statusCode();
            } else {
                value = response.jsonPath().get(source);
            }
            if (value == null) {
                throw new AssertionError("extract '" + name + "' üçün dəyər tapılmadı: " + source);
            }
            vars.put(name, value);
        });
    }
}
