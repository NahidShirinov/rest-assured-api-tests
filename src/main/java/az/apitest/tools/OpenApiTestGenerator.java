package az.apitest.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAPI (Swagger) spesifikasiyasından test faylı qaralaması yaradır.
 *
 *   mvn -q compile exec:java -Dopenapi=https://petstore3.swagger.io/api/v3/openapi.json -Dname=petstore
 *
 * Nəticə: src/test/resources/testdata/_<name>.json ("_" ilə başladığı üçün avtomatik İŞLƏMİR).
 * Faylı yoxla, dəyərləri düzəlt, sonra adından "_" işarəsini sil - testlər işləməyə başlayacaq.
 *
 * Hər əməliyyat üçün:
 *   - pozitiv test: nümunə body/parametrlər, ilk 2xx status, "openapi": true (cavab spesifikasiyaya uyğundurmu)
 *   - neqativ testlər: body-nin hər məcburi sahəsi olmadan -> 400, hər məcburi query parametri olmadan -> 400
 */
public final class OpenApiTestGenerator {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final List<PathItem.HttpMethod> ORDER = List.of(PathItem.HttpMethod.GET, PathItem.HttpMethod.POST,
            PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);
    private static final int MAX_DEPTH = 5;

    private OpenApiTestGenerator() {
    }

    public static void main(String[] args) throws IOException {
        String spec = System.getProperty("openapi");
        if (spec == null || spec.isBlank()) {
            System.err.println("İstifadə: mvn -q compile exec:java -Dopenapi=<URL və ya fayl> [-Dname=<ad>] [-Dout=<fayl>]");
            System.exit(1);
        }
        OpenAPI api = parse(spec);
        String name = System.getProperty("name", slug(api.getInfo() != null ? api.getInfo().getTitle() : "api"));
        Path out = Path.of(System.getProperty("out",
                Path.of(System.getProperty("basedir", "."), "src", "test", "resources", "testdata", "_" + name + ".json").toString()));

        ObjectNode suite = generate(api, spec, name);
        out = out.toAbsolutePath().normalize();
        Files.createDirectories(out.getParent());
        Files.writeString(out, MAPPER.writeValueAsString(suite) + "\n", StandardCharsets.UTF_8);
        System.out.println(suite.withArray("tests").size() + " test yaradıldı: " + out);
        System.out.println("Yoxla, dəyərləri düzəlt, sonra işə salmaq üçün faylın adından \"_\" işarəsini sil.");
    }

    public static OpenAPI parse(String spec) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = spec.startsWith("http://") || spec.startsWith("https://")
                ? new OpenAPIV3Parser().readLocation(spec, null, options)
                : readFile(spec, options);
        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException("OpenAPI oxunmadı: " + spec + " -> " + result.getMessages());
        }
        return result.getOpenAPI();
    }

    private static SwaggerParseResult readFile(String spec, ParseOptions options) {
        try {
            return new OpenAPIV3Parser().readContents(Files.readString(Path.of(spec)), null, options);
        } catch (IOException e) {
            throw new IllegalArgumentException("Fayl oxunmadı: " + spec, e);
        }
    }

    public static ObjectNode generate(OpenAPI api, String spec, String name) {
        ObjectNode suite = JSON.objectNode();
        suite.put("$schema", "../api-test.schema.json");
        suite.put("suite", api.getInfo() != null && api.getInfo().getTitle() != null ? api.getInfo().getTitle() : name);
        String baseUrl = baseUrl(api, spec);
        if (baseUrl != null) {
            suite.put("baseUrl", baseUrl);
        }
        suite.put("openapi", true);
        suite.put("openapiSpec", spec);
        ArrayNode tests = suite.putArray("tests");

        if (api.getPaths() != null) {
            api.getPaths().forEach((path, item) -> {
                Map<PathItem.HttpMethod, Operation> ops = item.readOperationsMap();
                for (PathItem.HttpMethod method : ORDER) {
                    Operation op = ops.get(method);
                    if (op != null) {
                        addTests(tests, path, method.name(), op, item.getParameters());
                    }
                }
            });
        }
        return suite;
    }

    private static void addTests(ArrayNode tests, String path, String method, Operation op, List<Parameter> pathLevel) {
        String title = op.getSummary() != null ? op.getSummary()
                : op.getOperationId() != null ? op.getOperationId() : method + " " + path;
        title = title.strip().replaceAll("\\.+$", "");
        List<Parameter> params = new ArrayList<>();
        if (pathLevel != null) params.addAll(pathLevel);
        if (op.getParameters() != null) params.addAll(op.getParameters());

        ObjectNode positive = baseTest(title, path, method, params, List.of("generated", "positive"));
        Schema<?> bodySchema = jsonSchema(op.getRequestBody() == null ? null : op.getRequestBody().getContent());
        if (bodySchema != null) {
            positive.set("body", MAPPER.valueToTree(sample(bodySchema, null, 0)));
        }
        positive.putObject("expect").put("status", successStatus(op));
        tests.add(positive);

        if (bodySchema != null && bodySchema.getRequired() != null) {
            for (String field : requiredInPropertyOrder(bodySchema)) {
                ObjectNode neg = baseTest(title + ": '" + field + "' olmadan -> 400", path, method, params,
                        List.of("generated", "negative"));
                neg.set("body", positive.get("body").deepCopy());
                neg.putArray("bodyRemove").add(field);
                neg.putObject("expect").put("status", 400);
                tests.add(neg);
            }
        }
        for (Parameter p : params) {
            if ("query".equals(p.getIn()) && Boolean.TRUE.equals(p.getRequired())) {
                ObjectNode neg = baseTest(title + ": '" + p.getName() + "' parametri olmadan -> 400", path, method, params,
                        List.of("generated", "negative"));
                ((ObjectNode) neg.get("queryParams")).remove(p.getName());
                if (neg.get("queryParams").isEmpty()) neg.remove("queryParams");
                if (positive.has("body")) neg.set("body", positive.get("body").deepCopy());
                neg.putObject("expect").put("status", 400);
                tests.add(neg);
            }
        }
    }

    /** Parser required siyahısını sıralaya bilir; testlər body-dəki sahə sırası ilə yaransın. */
    private static List<String> requiredInPropertyOrder(Schema<?> schema) {
        List<String> ordered = new ArrayList<>();
        if (schema.getProperties() != null) {
            schema.getProperties().keySet().stream().filter(schema.getRequired()::contains).forEach(ordered::add);
        }
        schema.getRequired().stream().filter(r -> !ordered.contains(r)).forEach(ordered::add);
        return ordered;
    }

    private static ObjectNode baseTest(String name, String path, String method, List<Parameter> params, List<String> tags) {
        ObjectNode test = JSON.objectNode();
        test.put("name", name);
        test.put("description", "OpenAPI-dən yaradılıb - dəyərləri yoxla");
        ArrayNode tagArray = test.putArray("tags");
        tags.forEach(tagArray::add);
        test.put("method", method);
        test.put("path", path);
        Map<String, Object> pathParams = new LinkedHashMap<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        for (Parameter p : params) {
            Object value = p.getExample() != null ? p.getExample() : sample(p.getSchema(), p.getName(), 0);
            switch (p.getIn() == null ? "" : p.getIn()) {
                case "path" -> pathParams.put(p.getName(), value);
                case "query" -> {
                    if (Boolean.TRUE.equals(p.getRequired())) queryParams.put(p.getName(), value);
                }
                case "header" -> {
                    if (Boolean.TRUE.equals(p.getRequired())) headers.put(p.getName(), String.valueOf(value));
                }
                default -> { }
            }
        }
        if (!headers.isEmpty()) test.set("headers", MAPPER.valueToTree(headers));
        if (!queryParams.isEmpty()) test.set("queryParams", MAPPER.valueToTree(queryParams));
        if (!pathParams.isEmpty()) test.set("pathParams", MAPPER.valueToTree(pathParams));
        return test;
    }

    private static int successStatus(Operation op) {
        if (op.getResponses() != null) {
            for (String code : op.getResponses().keySet()) {
                if (code.matches("2\\d\\d")) return Integer.parseInt(code);
            }
        }
        return 200;
    }

    private static Schema<?> jsonSchema(Content content) {
        if (content == null) return null;
        for (Map.Entry<String, MediaType> e : content.entrySet()) {
            if (e.getKey().contains("json") || e.getKey().equals("*/*")) {
                return e.getValue().getSchema();
            }
        }
        return null;
    }

    /** Spesifikasiyadakı nümunəni, yoxdursa tip/format/ada görə ağlabatan dəyər (çox vaxt ${...} generator). */
    static Object sample(Schema<?> schema, String name, int depth) {
        if (schema == null || depth > MAX_DEPTH) return null;
        if (schema.getExample() != null) return schema.getExample();
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) return schema.getEnum().get(0);
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Schema<?> part : schema.getAllOf()) {
                if (sample(part, name, depth + 1) instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> merged.put(String.valueOf(k), v));
                }
            }
            return merged;
        }
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) return sample(schema.getOneOf().get(0), name, depth + 1);
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) return sample(schema.getAnyOf().get(0), name, depth + 1);

        String type = type(schema);
        switch (type) {
            case "object" -> {
                Map<String, Object> object = new LinkedHashMap<>();
                if (schema.getProperties() != null) {
                    schema.getProperties().forEach((prop, propSchema) -> object.put(prop, sample(propSchema, prop, depth + 1)));
                }
                return object;
            }
            case "array" -> {
                List<Object> list = new ArrayList<>();
                list.add(sample(schema.getItems(), name, depth + 1));
                return list;
            }
            case "integer" -> {
                return schema.getMinimum() != null ? schema.getMinimum().intValue() : 1;
            }
            case "number" -> {
                return schema.getMinimum() != null ? schema.getMinimum() : 1.5;
            }
            case "boolean" -> {
                return true;
            }
            default -> {
                return stringSample(schema.getFormat(), name);
            }
        }
    }

    private static String type(Schema<?> schema) {
        if (schema.getType() != null) return schema.getType();
        if (schema.getTypes() != null) {
            for (String t : schema.getTypes()) {
                if (!"null".equals(t)) return t;
            }
        }
        if (schema.getProperties() != null) return "object";
        if (schema.getItems() != null) return "array";
        return "string";
    }

    private static String stringSample(String format, String name) {
        if (format != null) {
            switch (format) {
                case "email": return "${faker.internet.emailAddress}";
                case "uuid": return "${random.uuid}";
                case "date": return "${date.today}";
                case "date-time": return "${datetime.now}";
                case "uri", "url": return "https://example.com";
                case "password": return "Test1234!";
                default: break;
            }
        }
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "");
        if (n.contains("email")) return "${faker.internet.emailAddress}";
        if (n.contains("firstname")) return "${faker.name.firstName}";
        if (n.contains("lastname") || n.contains("surname")) return "${faker.name.lastName}";
        if (n.contains("username")) return "${faker.internet.username}";
        if (n.contains("phone")) return "${faker.phoneNumber.cellPhone}";
        if (n.contains("city")) return "${faker.address.city}";
        if (n.contains("country")) return "${faker.address.country}";
        if (n.contains("street") || n.contains("address")) return "${faker.address.streetAddress}";
        if (n.contains("zip") || n.contains("postal")) return "${faker.address.zipCode}";
        if (n.contains("name")) return "${faker.name.fullName}";
        return "${random.string}";
    }

    /** Server URL: mütləq URL olduğu kimi, nisbi (/api/v3) isə spesifikasiyanın host-u ilə birləşir. */
    private static String baseUrl(OpenAPI api, String spec) {
        if (api.getServers() == null || api.getServers().isEmpty()) return null;
        String url = api.getServers().get(0).getUrl();
        if (url == null || url.equals("/")) {
            return spec.startsWith("http") ? origin(spec) : null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        return spec.startsWith("http") ? origin(spec) + (url.startsWith("/") ? url : "/" + url) : null;
    }

    private static String origin(String spec) {
        URI uri = URI.create(spec);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String slug(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "api" : s;
    }
}
