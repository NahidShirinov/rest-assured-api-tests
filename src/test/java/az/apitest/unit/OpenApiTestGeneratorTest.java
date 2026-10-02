package az.apitest.unit;

import az.apitest.engine.SuiteLoader;
import az.apitest.tools.OpenApiTestGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class OpenApiTestGeneratorTest {

    private static final String SPEC = Path.of(System.getProperty("basedir", "."),
            "src", "test", "resources", "unit", "users-openapi.yaml").toString();

    private ObjectNode generate() {
        return OpenApiTestGenerator.generate(OpenApiTestGenerator.parse(SPEC), "unit/users-openapi.yaml", "users");
    }

    private static List<String> names(JsonNode suite) {
        List<String> names = new ArrayList<>();
        suite.get("tests").forEach(t -> names.add(t.get("name").asText()));
        return names;
    }

    @Test
    public void generatesPositiveAndNegativeTests() {
        ObjectNode suite = generate();
        assertEquals(names(suite), List.of(
                "Create user",
                "Create user: 'name' olmadan -> 400",
                "Create user: 'email' olmadan -> 400",
                "Get user",
                "getJob",
                "getJob: 'verbose' parametri olmadan -> 400"));
        assertTrue(suite.get("openapi").asBoolean());
    }

    @Test
    public void positiveTestHasSampleBodyAndSuccessStatus() {
        JsonNode create = generate().get("tests").get(0);
        assertEquals(create.get("method").asText(), "POST");
        assertEquals(create.get("expect").get("status").asInt(), 201);
        assertEquals(create.get("body").get("email").asText(), "${faker.internet.emailAddress}");
        assertEquals(create.get("body").get("name").asText(), "${faker.name.fullName}");
        assertEquals(create.get("body").get("role").asText(), "USER");
        assertEquals(create.get("body").get("address").get("city").asText(), "${faker.address.city}");
    }

    @Test
    public void negativeTestsRemoveRequiredFieldOrParameter() {
        JsonNode tests = generate().get("tests");
        assertEquals(tests.get(1).get("bodyRemove").toString(), "[\"name\"]");
        assertEquals(tests.get(1).get("expect").get("status").asInt(), 400);
        JsonNode getJob = tests.get(4);
        assertEquals(getJob.get("pathParams").get("id").asInt(), 1);
        assertEquals(getJob.get("queryParams").get("verbose").asBoolean(), true);
        assertTrue(!tests.get(5).has("queryParams"), tests.get(5).toString());
    }

    @Test
    public void generatedFileIsValidAndLoadable() throws Exception {
        Path out = Files.createTempFile("generated", ".json");
        Files.writeString(out, generate().toPrettyString());
        assertThat(Files.readString(out), matchesJsonSchemaInClasspath("api-test.schema.json"));
        assertEquals(SuiteLoader.read(out).tests.size(), 6);
        Files.delete(out);
    }
}
