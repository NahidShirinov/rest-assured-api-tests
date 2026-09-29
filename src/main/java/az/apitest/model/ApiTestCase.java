package az.apitest.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-dakı tək test: bir HTTP sorğu + gözləntilər + çıxarılan dəyişənlər. */
public class ApiTestCase {

    public String name;
    public String description;
    public boolean enabled = true;
    public List<String> tags = new ArrayList<>();

    public String method = "GET";
    public String path;
    public Map<String, String> headers = new LinkedHashMap<>();
    public Map<String, Object> queryParams = new LinkedHashMap<>();
    public Map<String, Object> pathParams = new LinkedHashMap<>();
    public Map<String, Object> formParams = new LinkedHashMap<>();
    public JsonNode body;

    public Expectation expect = new Expectation();

    /** Cavabdan dəyər çıxarıb növbəti testlərə ötürmək: {"postId": "id", "loc": "header:Location"} */
    public Map<String, String> extract = new LinkedHashMap<>();

    @Override
    public String toString() {
        return method + " " + path + " - " + name;
    }
}
