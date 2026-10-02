package az.apitest.model;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** Body-ni fayldan götür (src/test/resources-a nisbətən): "bodies/create-user.json" */
    public String bodyFile;

    /** Body-də sahələri dəyiş/əlavə et: {"role": "ADMIN", "address.city": "Baku", "items[0].qty": 2} */
    public Map<String, JsonNode> bodyOverrides = new LinkedHashMap<>();

    /** Body-dən sahələri sil (məcburi sahə yoxdursa testləri üçün): ["email", "address.zip"] */
    public List<String> bodyRemove = new ArrayList<>();

    /** Eyni testi bir neçə data ilə işlət: hər sətir ${açar} kimi əlçatandır. */
    public List<Map<String, Object>> dataSets = new ArrayList<>();

    /** Data sətirləri CSV faylından (birinci sətir = başlıqlar): "data/users.csv" */
    public String dataFile;

    /** Sorğunu + gözləntiləri şərt ödənənə qədər təkrarla (GET üçün; POST təkrar göndərilir!). */
    public AwaitConfig await;

    /** Cavabdan sonra baza yoxlamaları (tək obyekt və ya siyahı). */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<DbCheck> db = new ArrayList<>();

    public Expectation expect = new Expectation();

    /** Cavabdan dəyər çıxarıb növbəti testlərə ötürmək: {"postId": "id", "loc": "header:Location"} */
    public Map<String, String> extract = new LinkedHashMap<>();

    /** dataSets/dataFile-dan gələn bu sətrin dəyərləri (loader doldurur). */
    public transient Map<String, Object> data = new LinkedHashMap<>();

    @Override
    public String toString() {
        return method + " " + path + " - " + name;
    }
}
