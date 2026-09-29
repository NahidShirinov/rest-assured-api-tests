package az.apitest.core;

import az.apitest.config.Config;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.specification.RequestSpecification;

/**
 * Bütün sorğular üçün ortaq RequestSpecification: base URL, timeout, auth, logging.
 * Auth növü config-dən gəlir: auth.type = none | bearer | basic | apikey
 */
public final class SpecFactory {

    static {
        // Test uğursuz olanda request/response avtomatik konsola yazılır
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private SpecFactory() {
    }

    public static RequestSpecification create() {
        return create(Config.baseUrl());
    }

    public static RequestSpecification create(String baseUrl) {
        int timeout = Config.getInt("timeout.ms", 15000);

        RestAssuredConfig config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeout)
                        .setParam("http.socket.timeout", timeout))
                // onluq ədədlər Float yox, Double kimi qaytarılsın
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE));

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(config);

        applyAuth(builder);

        if (Config.getBool("log.all", false)) {
            builder.addFilter(new RequestLoggingFilter()).addFilter(new ResponseLoggingFilter());
        }
        return builder.build();
    }

    private static void applyAuth(RequestSpecBuilder builder) {
        String type = Config.get("auth.type", "none").toLowerCase();
        switch (type) {
            case "none" -> { }
            case "bearer" -> builder.addHeader("Authorization", "Bearer " + Config.require("auth.token"));
            case "apikey" -> builder.addHeader(Config.get("auth.header", "X-API-Key"), Config.require("auth.token"));
            case "basic" -> builder.setAuth(RestAssured.preemptive()
                    .basic(Config.require("auth.username"), Config.require("auth.password")));
            default -> throw new IllegalStateException("Naməlum auth.type: " + type);
        }
    }
}
