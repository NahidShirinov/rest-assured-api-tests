package az.apitest.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cavab üçün gözləntilər.
 *
 * body açarları JsonPath (GPath) ifadəsidir, dəyərlər isə matcher:
 *   "title": "foo"          -> bərabərdir
 *   "id": "notNull"         -> null deyil
 *   "size()": "gt:0"        -> 0-dan böyük
 *   "email": "regex:.+@.+"  -> regex
 * Tam siyahı: README.md
 */
public class Expectation {

    /** 200 və ya "${status}" (dataSets-də hər sətir öz statusunu verə bilər). */
    public Object status;
    public Long maxTimeMs;
    /** classpath-da JSON schema faylı, məs. "schemas/post.json" */
    public String schema;
    public Map<String, String> headers = new LinkedHashMap<>();
    public Map<String, JsonNode> body = new LinkedHashMap<>();
}
