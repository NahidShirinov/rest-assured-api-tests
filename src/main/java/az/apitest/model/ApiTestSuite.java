package az.apitest.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bir JSON faylı = bir suite. Testlər ardıcıl işləyir və dəyişənləri paylaşır. */
public class ApiTestSuite {

    /** Suite adı (hesabatda görünür). */
    public String suite;

    /** İstəyə görə: bu suite üçün base URL (boşdursa config-dəki base.url). */
    public String baseUrl;

    /** Başlanğıc dəyişənlər: ${ad} ilə istifadə olunur. */
    public Map<String, Object> variables = new LinkedHashMap<>();

    /** Suite-dəki bütün sorğulara əlavə olunan header-lər. */
    public Map<String, String> headers = new LinkedHashMap<>();

    public List<ApiTestCase> tests = new ArrayList<>();

    /** Hansı fayldan yüklənib (loader doldurur). */
    public transient String sourceFile;
}
