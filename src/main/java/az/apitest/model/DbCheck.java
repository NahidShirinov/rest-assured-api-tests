package az.apitest.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API sorğusundan sonra bazada yoxlama.
 *
 *   "db": {
 *     "query": "SELECT status FROM orders WHERE id = ?",
 *     "params": ["${orderId}"],
 *     "expect": { "size()": 1, "[0].status": "CREATED" }
 *   }
 *
 * Nəticə sətirlərin siyahısıdır: [ {"status": "CREATED", ...}, ... ]. Sütun adları kiçik hərflə.
 */
public class DbCheck {

    /** Hansı baza: config-də db.url (default) və ya db.<ad>.url, ya da suite-dəki "datasources". */
    public String datasource = "default";

    /** SQL. Dəyərləri "?" ilə ver, birbaşa mətnə yazma (SQL injection). */
    public String query;

    /** "?" yerinə qoyulan dəyərlər, ardıcıllıqla. ${...} dəstəklənir. */
    public List<Object> params = new ArrayList<>();

    /** Açar = GPath (size(), [0].status, findAll { it.amount > 10 }.size()), dəyər = matcher. */
    public Map<String, JsonNode> expect = new LinkedHashMap<>();

    /** Nəticədən dəyişən çıxarmaq: {"orderStatus": "[0].status"} */
    public Map<String, String> extract = new LinkedHashMap<>();

    /** Asinxron yazılar üçün: şərt ödənənə qədər sorğunu təkrarla. */
    public AwaitConfig await;
}
