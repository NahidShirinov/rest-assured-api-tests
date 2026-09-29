package az.apitest.core;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Kodla yazılan testlər üçün sadə HTTP client.
 *
 *   ApiClient api = new ApiClient();
 *   api.get("/users/{id}", 1).then().statusCode(200);
 *   api.post("/posts", new Post(...)).then().statusCode(201);
 */
public class ApiClient {

    private final RequestSpecification spec;

    public ApiClient() {
        this.spec = SpecFactory.create();
    }

    public ApiClient(String baseUrl) {
        this.spec = SpecFactory.create(baseUrl);
    }

    /** Xüsusi hallarda (multipart, cookie, və s.) tam REST Assured imkanı üçün. */
    public RequestSpecification request() {
        return given().spec(spec);
    }

    public Response get(String path, Object... pathParams) {
        return request().get(path, pathParams);
    }

    public Response get(String path, Map<String, ?> queryParams) {
        return request().queryParams(queryParams).get(path);
    }

    public Response post(String path, Object body) {
        return request().body(body).post(path);
    }

    public Response put(String path, Object body, Object... pathParams) {
        return request().body(body).put(path, pathParams);
    }

    public Response patch(String path, Object body, Object... pathParams) {
        return request().body(body).patch(path, pathParams);
    }

    public Response delete(String path, Object... pathParams) {
        return request().delete(path, pathParams);
    }
}
