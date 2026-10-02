package az.apitest.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit testlər üçün lokal HTTP server (unit/users-openapi.yaml-ı təqlid edir, şəbəkə lazım deyil).
 *
 *   POST /users      name və email varsa 201 + body (id=1 ilə), yoxdursa 400
 *   GET  /users/1    200, spesifikasiyaya uyğun
 *   GET  /users/2    200, amma id mətn ("two") - spesifikasiyaya UYĞUN DEYİL
 *   GET  /jobs/{id}  ilk 2 çağırışda RUNNING, sonra DONE (await üçün)
 */
final class LocalApi implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final Map<String, AtomicInteger> jobCalls = new ConcurrentHashMap<>();
    volatile JsonNode lastUserBody;

    LocalApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", this::users);
        server.createContext("/jobs/", this::jobs);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void users(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (ex.getRequestMethod().equals("POST") && path.equals("/users")) {
            JsonNode body = MAPPER.readTree(ex.getRequestBody());
            lastUserBody = body;
            if (!body.hasNonNull("name") || !body.hasNonNull("email") || body.get("email").asText().isEmpty()) {
                send(ex, 400, "{\"error\":\"name and email are required\"}");
                return;
            }
            ObjectNode created = ((ObjectNode) body.deepCopy()).put("id", 1);
            send(ex, 201, created.toString());
        } else if (path.equals("/users/1")) {
            send(ex, 200, "{\"id\":1,\"name\":\"Ali\",\"email\":\"ali@test.com\"}");
        } else if (path.equals("/users/2")) {
            send(ex, 200, "{\"id\":\"two\",\"name\":\"Bad\",\"email\":\"bad@test.com\"}");
        } else {
            send(ex, 404, "{\"error\":\"not found\"}");
        }
    }

    private void jobs(HttpExchange ex) throws IOException {
        int calls = jobCalls.computeIfAbsent(ex.getRequestURI().getPath(), k -> new AtomicInteger()).incrementAndGet();
        send(ex, 200, "{\"status\":\"" + (calls < 3 ? "RUNNING" : "DONE") + "\",\"calls\":" + calls + "}");
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
