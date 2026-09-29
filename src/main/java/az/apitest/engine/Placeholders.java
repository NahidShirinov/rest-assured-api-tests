package az.apitest.engine;

import az.apitest.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ${...} dəyişənlərini həll edir.
 *
 *   ${postId}          -> suite dəyişəni (variables və ya extract ilə yaradılan)
 *   ${config.key}      -> config faylından
 *   ${env.NAME}        -> OS environment dəyişəni
 *   ${random.uuid} ${random.int} ${random.email} ${random.string} ${timestamp}
 *   ${random.digits:16} -> 16 rəqəmli string (kart nömrəsi, telefon və s.)
 *
 * Dəyər tam olaraq "${x}"-dirsə tipi qorunur (ədəd ədəd kimi qalır).
 */
public final class Placeholders {

    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Placeholders() {
    }

    public static Object lookup(String key, Map<String, Object> vars) {
        if (vars.containsKey(key)) {
            Object value = vars.get(key);
            if (value instanceof DependencyFailedException.Unavailable unavailable) {
                throw new DependencyFailedException(key, unavailable);
            }
            return value;
        }
        if (key.startsWith("random.digits:")) {
            return randomDigits(Integer.parseInt(key.substring("random.digits:".length())));
        }
        return switch (key) {
            case "random.uuid" -> UUID.randomUUID().toString();
            case "random.int" -> ThreadLocalRandom.current().nextInt(1, 1_000_000);
            case "random.email" -> "user_" + shortId() + "@test.com";
            case "random.string" -> "str_" + shortId();
            case "timestamp" -> System.currentTimeMillis();
            default -> {
                if (key.startsWith("config.")) {
                    yield Config.require(key.substring("config.".length()));
                }
                if (key.startsWith("env.")) {
                    String value = System.getenv(key.substring("env.".length()));
                    if (value == null) {
                        throw new IllegalArgumentException("ENV dəyişəni tapılmadı: " + key);
                    }
                    yield value;
                }
                throw new IllegalArgumentException(
                        "Naməlum dəyişən ${" + key + "}. Mövcud olanlar: " + vars.keySet());
            }
        };
    }

    /** String daxilindəki bütün ${...}-ləri mətnlə əvəz edir. */
    public static String resolve(String text, Map<String, Object> vars) {
        if (text == null) {
            return null;
        }
        Matcher m = PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(lookup(m.group(1), vars))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** "${x}" tam dəyərdirsə orijinal tipi qaytarır, əks halda string. */
    public static Object resolveTyped(String text, Map<String, Object> vars) {
        Matcher m = PATTERN.matcher(text);
        if (m.matches()) {
            return lookup(m.group(1), vars);
        }
        return resolve(text, vars);
    }

    /** JSON ağacında (body, expect) bütün string-ləri həll edir. */
    public static JsonNode resolve(JsonNode node, Map<String, Object> vars) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return MAPPER.valueToTree(resolveTyped(node.asText(), vars));
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                copy.set(resolve(e.getKey(), vars), resolve(e.getValue(), vars));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(child -> copy.add(resolve(child, vars)));
            return copy;
        }
        return node;
    }

    public static Object resolveValue(Object value, Map<String, Object> vars) {
        return value instanceof String s ? resolveTyped(s, vars) : value;
    }

    /** Sabit uzunluqlu rəqəm sətri (string kimi, baş sıfırlar itmir). */
    private static String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
