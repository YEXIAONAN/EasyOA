package com.easyoa;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real HTTP exercises Tomcat's multipart limits, which MockMvc does not enforce. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "easyoa.storage.max-file-size=20971520")
class MultipartBoundaryIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    @Test
    void maximumSizeFileUploadsWithMultipartOverheadButLargerFileIsRejected() throws Exception {
        var root = initializeSystemWithRoot("multipart_root");
        data(jsonPost("/api/auth/login", Map.of("username", root.getUsername(), "password", DEFAULT_PASSWORD)));
        long projectId = data(jsonPost("/api/projects", Map.of(
                "name", "Multipart boundary", "activateImmediately", true))).path("id").asLong();
        long taskId = data(jsonPost("/api/projects/" + projectId + "/tasks", Map.of(
                "title", "Maximum file size", "primaryAssigneeId", root.getId()))).path("id").asLong();

        byte[] file = new byte[20 * 1024 * 1024];
        Arrays.fill(file, (byte) 'x');
        JsonNode uploaded = data(upload(taskId, file));
        assertEquals(file.length, uploaded.path("size").asLong());
        String expectedHash = sha256(file);
        assertEquals(expectedHash, uploaded.path("sha256").asText());
        var download = request("GET", uploaded.path("downloadUrl").asText(),
                HttpRequest.BodyPublishers.noBody(), null);
        assertEquals(200, download.statusCode());
        assertEquals(file.length, download.body().length);
        assertEquals(expectedHash, sha256(download.body()));

        var rejected = upload(taskId, Arrays.copyOf(file, file.length + 1));
        assertEquals(413, rejected.statusCode());
        assertEquals("PAYLOAD_TOO_LARGE", objectMapper.readTree(rejected.body()).path("code").asText());
        assertEquals(1, data(request("GET", "/api/tasks/" + taskId + "/files",
                HttpRequest.BodyPublishers.noBody(), null)).size());
    }

    private HttpResponse<byte[]> upload(long taskId, byte[] file) throws Exception {
        String boundary = "EasyOAMultipartBoundaryTest";
        byte[] header = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
                + "filename=\"boundary.txt\"\r\nContent-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return request("POST", "/api/tasks/" + taskId + "/files", HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(header), HttpRequest.BodyPublishers.ofByteArray(file),
                HttpRequest.BodyPublishers.ofByteArray(footer)), "multipart/form-data; boundary=" + boundary);
    }

    private HttpResponse<byte[]> jsonPost(String path, Map<String, Object> body) throws Exception {
        return request("POST", path, HttpRequest.BodyPublishers.ofString(json(body)), "application/json");
    }

    private HttpResponse<byte[]> request(String method, String path, HttpRequest.BodyPublisher body,
            String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (!method.equals("GET")) {
            var csrf = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/csrf"))
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(200, csrf.statusCode());
            String token = cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                    .findFirst().orElseThrow().getValue();
            builder.header("X-XSRF-TOKEN", token);
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return client.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode data(HttpResponse<byte[]> response) throws Exception {
        assertEquals(200, response.statusCode(), () -> new String(response.body(), StandardCharsets.UTF_8));
        return objectMapper.readTree(response.body()).path("data");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
