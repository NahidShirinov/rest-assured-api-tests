package az.apitest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bir JSON faylı = bir suite. Testlər ardıcıl işləyir və dəyişənləri paylaşır. */
public class ApiTestSuite {

    /** IDE üçün JSON Schema linki ("$schema": "../api-test.schema.json"); framework istifadə etmir. */
    @JsonProperty("$schema")
    public String jsonSchema;

    /** Suite adı (hesabatda görünür). */
    public String suite;

    /** İstəyə görə: bu suite üçün base URL (boşdursa config-dəki base.url). */
    public String baseUrl;

    /** Başlanğıc dəyişənlər: ${ad} ilə istifadə olunur. */
    public Map<String, Object> variables = new LinkedHashMap<>();

    /** Suite-dəki bütün sorğulara əlavə olunan header-lər. */
    public Map<String, String> headers = new LinkedHashMap<>();

    /** Bütün testlərdə cavabı OpenAPI spesifikasiyasına qarşı yoxla (test səviyyəsində "expect.openapi" ilə dəyişmək olar). */
    public Boolean openapi;

    /** OpenAPI spesifikasiyası (URL və ya fayl). Yoxdursa config-dəki openapi.spec. */
    public String openapiSpec;

    /** Suite-in baza bağlantıları: "db": {"datasource": "<ad>"} ilə istifadə olunur. */
    public Map<String, DataSource> datasources = new LinkedHashMap<>();

    public List<ApiTestCase> tests = new ArrayList<>();

    /** Hansı fayldan yüklənib (loader doldurur). */
    public transient String sourceFile;
}
