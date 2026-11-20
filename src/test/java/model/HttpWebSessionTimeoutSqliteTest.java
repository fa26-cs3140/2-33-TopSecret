package model;

import controller.ContentController;
import controller.HttpWebServer;
import controller.SqliteWebSessionStore;
import controller.StoredWebAuthentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HttpWebSessionTimeoutSqliteTest {

    @TempDir Path temporary;

    @Test
    void anIdleWebSessionExpiresActivityKeepsItAliveAndTheTerminalCanRevokeIt() throws Exception {
        Path database = temporary.resolve("missions.db");
        FileHandler files = new FileHandlerImpl();
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService content =
                new SqliteContentService(database, audit, mock(FileHandler.class));
        content.initialize();
        AuthenticationService auth = new AuthenticationService(new FileCredentialStore(
                temporary.resolve("credentials.cip"), files, new SubstitutionCipher(files), "key.txt"));
        auth.createCredentials("admin", "Secret1");
        MutableClock clock = new MutableClock();
        SqliteWebSessionStore sessions =
                new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
        sessions.initialize();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        try (HttpWebServer server = new HttpWebServer(new StoredWebAuthentication(auth), sessions,
                new ContentController(content, auth), new InetSocketAddress("localhost", 0))) {
            server.start();
            String base = "http://localhost:" + server.address().getPort();

            assertEquals(303, post(client, base + "/login", "username=admin&password=Secret1").statusCode());
            assertEquals(200, get(client, base + "/menu").statusCode());

            clock.advance(Duration.ofMinutes(25));
            assertEquals(200, get(client, base + "/menu").statusCode());
            clock.advance(Duration.ofMinutes(25));
            assertEquals(200, get(client, base + "/menu").statusCode(),
                    "50 minutes in total, but never more than 25 idle");

            clock.advance(Duration.ofMinutes(31));
            assertEquals(303, get(client, base + "/menu").statusCode(), "idle past 30 minutes");

            assertEquals(303, post(client, base + "/login", "username=admin&password=Secret1").statusCode());
            assertEquals(200, get(client, base + "/menu").statusCode());

            SqliteWebSessionStore terminal =
                    new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
            terminal.initialize();
            WebSessionInfo only = terminal.activeSessions().get(0);
            assertEquals("admin", only.username());
            assertTrue(terminal.revokeSession(only.id()));
            assertEquals(303, get(client, base + "/menu").statusCode(), "revoked from the terminal");
        }
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}