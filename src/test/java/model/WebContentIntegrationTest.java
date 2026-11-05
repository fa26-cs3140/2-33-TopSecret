package model;

import controller.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WebContentIntegrationTest {
    @TempDir
    Path temporary;

    @Test
    void realCredentialsSeededSqliteReadsRolesAndAuditWorkOverHttp() throws Exception {
        Path database = temporary.resolve("missions.db");
        FileHandler files = new FileHandlerImpl();
        new SqliteMissionRepository(files, database, "mission_briefs.tsv").initialize();
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService content = new SqliteContentService(database, audit);
        content.initialize();
        AuthenticationService auth = new AuthenticationService(new FileCredentialStore(temporary.resolve("credentials.cip"),
                files, new SubstitutionCipher(files), "key.txt"));
        auth.createCredentials("admin", "Secret1");
        auth.createUser("Ghost");
        auth.setInitialPassword("Ghost", "Ghost1", "Ghost1");
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        try (HttpWebServer server = new HttpWebServer(new StoredWebAuthentication(auth), new InMemoryWebSessionStore(),
                new ContentController(content, auth), new InetSocketAddress("localhost", 0))) {
            server.start();
            String base = "http://localhost:" + server.address().getPort();
            assertEquals(303, post(client, base + "/login", "username=admin&password=Secret1").statusCode());
            for (String type : List.of("briefs", "agents", "facilities", "missions")) {
                HttpResponse<String> response = get(client, base + "/content?type=" + type);
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("/record?type=" + type + "&amp;id="));
            }
            assertTrue(get(client, base + "/content?type=agents").body().contains("Ghost"));
            assertEquals(200, get(client, base + "/record?type=briefs&id=1").statusCode());
            assertEquals(404, get(client, base + "/record?type=briefs&id=2147483647").statusCode());
            assertEquals(200, get(client, base + "/audit").statusCode());
            assertTrue(audit.getAllEntries().stream().anyMatch(entry -> entry.getUsername().equals("admin")
                    && entry.getEventType() == AuditEventType.READ));
            int originalSize = content.list(ContentType.AGENT, auth.sessionFor("admin")).size();
            assertEquals(405, post(client, base + "/content?type=agents", "action=add&name=intruder").statusCode());
            assertEquals(originalSize, content.list(ContentType.AGENT, auth.sessionFor("admin")).size());
            assertEquals(303, post(client, base + "/logout", "").statusCode());
            assertEquals(303, post(client, base + "/login", "username=Ghost&password=Ghost1").statusCode());
            assertEquals(403, get(client, base + "/audit").statusCode());
            auth.promote("admin", "Ghost");
            assertEquals(200, get(client, base + "/audit").statusCode());
            auth.demote("admin", "Ghost");
            assertEquals(403, get(client, base + "/audit").statusCode());
        }
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
