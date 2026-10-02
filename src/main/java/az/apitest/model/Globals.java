package az.apitest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * testdata/_globals.json - bütün suite-lərə tətbiq olunan ortaq dəyərlər.
 * Suite-in öz dəyərləri eyni adlı qlobal dəyəri üstələyir.
 */
public class Globals {

    @JsonProperty("$schema")
    public String jsonSchema;

    public Map<String, Object> variables = new LinkedHashMap<>();
    public Map<String, String> headers = new LinkedHashMap<>();
    public Map<String, DataSource> datasources = new LinkedHashMap<>();
    public Boolean openapi;
    public String openapiSpec;
}
