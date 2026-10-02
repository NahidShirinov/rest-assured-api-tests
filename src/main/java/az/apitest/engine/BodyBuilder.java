package az.apitest.engine;

import az.apitest.model.ApiTestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Request body-ni qurur: body (və ya bodyFile) + bodyOverrides + bodyRemove.
 *
 * Yol sintaksisi: "name", "address.city", "items[0].qty", "[1].id" (kök massiv).
 * Override-da olmayan ara obyektlər yaradılır.
 */
public final class BodyBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    private BodyBuilder() {
    }

    /** null = body yoxdur. Nəticədə ${...} hələ həll olunmayıb. */
    public static JsonNode build(ApiTestCase tc) {
        JsonNode base = tc.body;
        if (tc.bodyFile != null) {
            if (base != null && !base.isMissingNode()) {
                throw new IllegalArgumentException("'" + tc.name + "': body və bodyFile birlikdə ola bilməz");
            }
            try {
                base = MAPPER.readTree(Resources.read(tc.bodyFile));
            } catch (IOException e) {
                throw new UncheckedIOException("bodyFile JSON deyil: " + tc.bodyFile, e);
            }
        }
        if (base == null || base.isMissingNode()) {
            if (!tc.bodyOverrides.isEmpty() || !tc.bodyRemove.isEmpty()) {
                throw new IllegalArgumentException("'" + tc.name + "': bodyOverrides/bodyRemove üçün body və ya bodyFile lazımdır");
            }
            return null;
        }
        JsonNode result = base.deepCopy();
        for (Map.Entry<String, JsonNode> e : tc.bodyOverrides.entrySet()) {
            set(result, e.getKey(), e.getValue() == null ? NullNode.getInstance() : e.getValue());
        }
        for (String path : tc.bodyRemove) {
            remove(result, path);
        }
        return result;
    }

    static void set(JsonNode root, String path, JsonNode value) {
        List<Object> segments = parse(path);
        JsonNode node = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            Object seg = segments.get(i);
            Object next = segments.get(i + 1);
            JsonNode child = child(node, seg);
            if (child == null || child.isNull()) {
                child = next instanceof Integer ? MAPPER.createArrayNode() : MAPPER.createObjectNode();
                put(node, seg, child, path);
            }
            node = child;
        }
        put(node, segments.get(segments.size() - 1), value, path);
    }

    static void remove(JsonNode root, String path) {
        List<Object> segments = parse(path);
        JsonNode node = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            node = child(node, segments.get(i));
            if (node == null) {
                throw new IllegalArgumentException("bodyRemove: '" + path + "' body-də yoxdur");
            }
        }
        Object last = segments.get(segments.size() - 1);
        boolean removed = last instanceof Integer idx
                ? node instanceof ArrayNode arr && idx < arr.size() && arr.remove(idx) != null
                : node instanceof ObjectNode obj && obj.remove((String) last) != null;
        if (!removed) {
            throw new IllegalArgumentException("bodyRemove: '" + path + "' body-də yoxdur");
        }
    }

    private static JsonNode child(JsonNode node, Object seg) {
        return seg instanceof Integer idx ? node.get(idx) : node.get((String) seg);
    }

    private static void put(JsonNode node, Object seg, JsonNode value, String path) {
        if (seg instanceof Integer idx) {
            if (!(node instanceof ArrayNode arr)) {
                throw new IllegalArgumentException("bodyOverrides: '" + path + "' - massiv gözlənilirdi");
            }
            while (arr.size() <= idx) {
                arr.addNull();
            }
            arr.set(idx, value);
        } else {
            if (!(node instanceof ObjectNode obj)) {
                throw new IllegalArgumentException("bodyOverrides: '" + path + "' - obyekt gözlənilirdi");
            }
            obj.set((String) seg, value);
        }
    }

    private static List<Object> parse(String path) {
        List<Object> segments = new ArrayList<>();
        Matcher m = SEGMENT.matcher(path);
        int end = 0;
        while (m.find()) {
            if (m.start() != end && path.charAt(end) != '.') {
                throw new IllegalArgumentException("Yanlış yol: " + path);
            }
            segments.add(m.group(1) != null ? m.group(1) : Integer.valueOf(m.group(2)));
            end = m.end();
        }
        if (segments.isEmpty() || end != path.length()) {
            throw new IllegalArgumentException("Yanlış yol: " + path);
        }
        return segments;
    }
}
