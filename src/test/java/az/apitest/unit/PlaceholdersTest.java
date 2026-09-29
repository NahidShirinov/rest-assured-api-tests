package az.apitest.unit;

import az.apitest.engine.Placeholders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class PlaceholdersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> vars = new HashMap<>(Map.of("id", 42, "name", "Ali"));

    @Test
    public void replacesVariablesInsideText() {
        assertEquals(Placeholders.resolve("/users/${id}/name/${name}", vars), "/users/42/name/Ali");
    }

    @Test
    public void textWithoutPlaceholdersIsUnchanged() {
        assertEquals(Placeholders.resolve("/users", vars), "/users");
    }

    @Test
    public void wholeValuePlaceholderKeepsType() {
        assertEquals(Placeholders.resolveTyped("${id}", vars), 42);
        assertEquals(Placeholders.resolveTyped("id=${id}", vars), "id=42");
    }

    @Test
    public void resolvesJsonTreeAndKeepsNumberTypes() throws Exception {
        JsonNode body = MAPPER.readTree("{\"id\": \"${id}\", \"tags\": [\"${name}\", \"x-${id}\"], \"n\": 1}");
        JsonNode resolved = Placeholders.resolve(body, vars);
        assertEquals(resolved.toString(), "{\"id\":42,\"tags\":[\"Ali\",\"x-42\"],\"n\":1}");
    }

    @Test
    public void unknownVariableFailsWithClearMessage() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> Placeholders.resolve("${nope}", vars));
        assertTrue(e.getMessage().contains("${nope}"), e.getMessage());
    }

    @Test
    public void randomDigitsHasExactLengthAndIsString() {
        Object value = Placeholders.resolveTyped("${random.digits:16}", vars);
        assertTrue(value instanceof String);
        assertTrue(((String) value).matches("\\d{16}"), value.toString());
    }

    @Test
    public void randomValuesDifferEachTime() {
        assertNotEquals(Placeholders.resolve("${random.uuid}", vars), Placeholders.resolve("${random.uuid}", vars));
        assertTrue(Placeholders.resolve("${random.email}", vars).matches("user_\\w+@test\\.com"));
    }

    @Test
    public void readsConfigValues() {
        assertTrue(Placeholders.resolve("${config.base.url}", vars).startsWith("http"));
    }
}
