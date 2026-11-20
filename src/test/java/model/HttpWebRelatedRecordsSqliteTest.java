package model;

import controller.ContentController;
import controller.HttpWebServer;
import controller.InMemoryWebSessionStore;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The web pages follow relationships in both directions and search every record. */
class HttpWebRelatedRecordsSqliteTest {

    @TempDir Path temporary;

    @Test
    void recordPagesLinkToRelatedRecordsAndSearchFindsThem() throws Exception {
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
        Session admin = auth.sessionFor("admin");
        content.add(ContentType.COUNTRY, List.of("France"), admin);
        content.add(ContentType.FACILITY, List.of("Langley Annex", "LGA"), admin);
        content.add(ContentType.AGENT,
                List.of("Cardinal", "1931-02-14", "1979-04-02", "Langley.", "1"), admin);
        content.addMission(
                List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05"), 1, 1, admin);
        content.add(ContentType.BRIEF,
                List.of("Gate report", "2026-11-06", "The north gate was unguarded."), admin);
        content.attachBrief(1, 1, admin);
        content.setCountry(1, 1, admin);

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        try (HttpWebServer server = new HttpWebServer(new StoredWebAuthentication(auth),
                new InMemoryWebSessionStore(), new ContentController(content, auth),
                new InetSocketAddress("localhost", 0))) {
            server.start();
            String base = "http://localhost:" + server.address().getPort();
            assertEquals(303, post(client, base + "/login", "username=admin&password=Secret1").statusCode());

            String facility = get(client, base + "/record?type=facilities&id=1").body();
            assertTrue(facility.contains("href=\"/record?type=agents&amp;id=1\""), facility);
            assertTrue(facility.contains("href=\"/record?type=missions&amp;id=1\""), facility);
            assertTrue(facility.contains("href=\"/record?type=countries&amp;id=1\""), facility);

            String agent = get(client, base + "/record?type=agents&id=1").body();
            assertTrue(agent.contains("href=\"/record?type=facilities&amp;id=1\""), agent);
            assertTrue(agent.contains("href=\"/record?type=missions&amp;id=1\""), agent);

            String country = get(client, base + "/record?type=countries&id=1").body();
            assertTrue(country.contains("href=\"/record?type=facilities&amp;id=1\""), country);

            String brief = get(client, base + "/record?type=briefs&id=1").body();
            assertTrue(brief.contains("href=\"/record?type=missions&amp;id=1\""), brief);

            String mission = get(client, base + "/record?type=missions&id=1").body();
            assertTrue(mission.contains("href=\"/record?type=facilities&amp;id=1\""), mission);
            assertTrue(mission.contains("href=\"/record?type=agents&amp;id=1\""), mission);
            assertTrue(mission.contains("href=\"/record?type=briefs&amp;id=1\""), mission);

            assertTrue(get(client, base + "/menu").body().contains("href=\"/search\""));

            String blank = get(client, base + "/search").body();
            assertTrue(blank.contains("name=\"q\""), blank);
            assertFalse(blank.contains("No matches."), blank);

            String found = get(client, base + "/search?q=north+gate").body();
            assertTrue(found.contains("href=\"/record?type=missions&amp;id=1\""), found);
            assertTrue(found.contains("href=\"/record?type=briefs&amp;id=1\""), found);

            assertTrue(get(client, base + "/search?q=zzzz").body().contains("No matches."));
            assertEquals(200, get(client, base + "/search?q=%25").statusCode());
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