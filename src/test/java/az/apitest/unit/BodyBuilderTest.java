package az.apitest.unit;

import az.apitest.engine.BodyBuilder;
import az.apitest.model.ApiTestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class BodyBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ApiTestCase withBody(String json) throws Exception {
        ApiTestCase tc = new ApiTestCase();
        tc.name = "t";
        tc.body = json == null ? null : MAPPER.readTree(json);
        return tc;
    }

    @Test
    public void noBodyGivesNull() throws Exception {
        assertNull(BodyBuilder.build(withBody(null)));
    }

    @Test
    public void readsBodyFileWithoutResolvingPlaceholders() {
        ApiTestCase tc = new ApiTestCase();
        tc.bodyFile = "unit/user-body.json";
        JsonNode body = BodyBuilder.build(tc);
        assertEquals(body.get("name").asText(), "Ali");
        assertEquals(body.get("email").asText(), "${random.email}");
    }

    @Test
    public void overridesNestedAndArrayPathsAndCreatesMissingParents() throws Exception {
        ApiTestCase tc = withBody("{\"name\":\"A\",\"items\":[{\"qty\":1}],\"address\":{\"city\":\"X\"}}");
        tc.bodyOverrides.put("name", TextNode.valueOf("B"));
        tc.bodyOverrides.put("items[0].qty", IntNode.valueOf(5));
        tc.bodyOverrides.put("address.city", TextNode.valueOf("Baku"));
        tc.bodyOverrides.put("meta.tags[1]", TextNode.valueOf("x"));
        tc.bodyOverrides.put("note", NullNode.getInstance());
        assertEquals(BodyBuilder.build(tc).toString(),
                "{\"name\":\"B\",\"items\":[{\"qty\":5}],\"address\":{\"city\":\"Baku\"},\"meta\":{\"tags\":[null,\"x\"]},\"note\":null}");
    }

    @Test
    public void removesFieldsAndArrayItems() throws Exception {
        ApiTestCase tc = withBody("{\"name\":\"A\",\"email\":\"e\",\"items\":[1,2,3],\"address\":{\"zip\":\"1\",\"city\":\"X\"}}");
        tc.bodyRemove.add("email");
        tc.bodyRemove.add("address.zip");
        tc.bodyRemove.add("items[0]");
        JsonNode body = BodyBuilder.build(tc);
        assertFalse(body.has("email"));
        assertEquals(body.get("address").toString(), "{\"city\":\"X\"}");
        assertEquals(body.get("items").toString(), "[2,3]");
    }

    @Test
    public void doesNotModifyOriginalBody() throws Exception {
        ApiTestCase tc = withBody("{\"name\":\"A\"}");
        tc.bodyRemove.add("name");
        BodyBuilder.build(tc);
        assertTrue(tc.body.has("name"));
    }

    @Test
    public void removingMissingFieldFails() throws Exception {
        ApiTestCase tc = withBody("{\"name\":\"A\"}");
        tc.bodyRemove.add("emial");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> BodyBuilder.build(tc));
        assertEquals(e.getMessage(), "bodyRemove: 'emial' body-də yoxdur");
    }

    @Test
    public void bodyAndBodyFileTogetherFail() throws Exception {
        ApiTestCase tc = withBody("{}");
        tc.bodyFile = "unit/user-body.json";
        expectThrows(IllegalArgumentException.class, () -> BodyBuilder.build(tc));
    }

    @Test
    public void overridesWithoutBodyFail() throws Exception {
        ApiTestCase tc = withBody(null);
        tc.bodyRemove.add("x");
        expectThrows(IllegalArgumentException.class, () -> BodyBuilder.build(tc));
    }
}
