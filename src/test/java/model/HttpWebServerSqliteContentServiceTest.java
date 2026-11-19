package model;

import controller.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HttpWebServerSqliteContentServiceTest {
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
            for (String type : List.of("briefs", "agents", "facilities", "missions", "countries")) {
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

    @Test
    void aFacilityShowsItsCountryByNameAndAnAdministratorChangesIt() throws Exception {
        Path database = temporary.resolve("missions.db");
        FileHandler files = new FileHandlerImpl();
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService content = new SqliteContentService(database, audit);
        content.initialize();
        AuthenticationService auth = new AuthenticationService(new FileCredentialStore(
                temporary.resolve("credentials.cip"), files, new SubstitutionCipher(files), "key.txt"));
        auth.createCredentials("admin", "Secret1");
        Session admin = auth.sessionFor("admin");

        // The supplied list is imported at initialization, not invented here.
        List<ContentEntry> countries = content.list(ContentType.COUNTRY, admin);
        assertTrue(countries.size() > 100, "expected the supplied country list to be imported");
        int france = idOf(countries, "France");
        int albania = idOf(countries, "Albania");

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        try (HttpWebServer server = new HttpWebServer(new StoredWebAuthentication(auth),
                new InMemoryWebSessionStore(), new ContentController(content, auth),
                new InetSocketAddress("localhost", 0))) {
            server.start();
            String base = "http://localhost:" + server.address().getPort();
            assertEquals(303, post(client, base + "/login", "username=admin&password=Secret1").statusCode());

            // The new-facility form offers the countries as a select.
            String form = get(client, base + "/new?type=facilities").body();
            assertTrue(form.contains("name=\"country_id\""), form);
            assertTrue(form.contains("France"), "the country options should be listed");

            int created = create(client, base, "name=Blackwood+Annex&abbreviation=BWA&country_id=" + france);

            // Viewing the facility shows the country by name, never its identifier.
            String record = get(client, base + "/record?type=facilities&id=" + created).body();
            assertTrue(record.contains("France"), record);
            assertTrue(record.contains("country"), record);
            assertFalse(record.contains(">" + france + "<"), "the raw country id should not be displayed");

            // The edit form preselects the country the facility currently has.
            String edit = get(client, base + "/edit?type=facilities&id=" + created).body();
            assertTrue(edit.contains("value=\"" + france + "\" selected"), edit);

            // Changing the country reassigns the facility.
            String csrf = csrf(get(client, base + "/edit?type=facilities&id=" + created).body());
            assertEquals(303, post(client, base + "/update", "type=facilities&id=" + created
                    + "&csrf=" + csrf + "&name=Blackwood+Annex&abbreviation=BWA&country_id="
                    + albania).statusCode());
            assertEquals("Albania",
                    content.countryFor(created, admin).orElseThrow().values().get(0));
            assertTrue(get(client, base + "/record?type=facilities&id=" + created).body()
                    .contains("Albania"));

            // Renaming the country shows through at the facility, because the
            // facility reads it through the key rather than storing a copy.
            String countryCsrf = csrf(get(client, base + "/edit?type=countries&id=" + albania).body());
            assertEquals(303, post(client, base + "/update", "type=countries&id=" + albania
                    + "&csrf=" + countryCsrf + "&name=Republic+of+Albania").statusCode());
            assertTrue(get(client, base + "/record?type=facilities&id=" + created).body()
                    .contains("Republic of Albania"));

            // A facility predating the country column reads as unassigned.
            content.add(ContentType.FACILITY, List.of("Older Site", "OLD"), admin);
            int older = content.list(ContentType.FACILITY, admin).stream()
                    .filter(entry -> entry.values().get(0).equals("Older Site"))
                    .findFirst().orElseThrow().id();
            assertTrue(get(client, base + "/record?type=facilities&id=" + older).body()
                    .contains("Unassigned"));
        }
    }

    private int create(HttpClient client, String base, String body) throws Exception {
        String csrf = csrf(get(client, base + "/new?type=facilities").body());
        HttpResponse<String> response = post(client, base + "/create",
                "type=facilities&csrf=" + csrf + "&" + body);
        assertEquals(303, response.statusCode(), response.body());
        String page = get(client, base + "/content?type=facilities").body();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("id=(\\d+)\"").matcher(page);
        int last = 0;
        while (matcher.find()) {
            last = Math.max(last, Integer.parseInt(matcher.group(1)));
        }
        return last;
    }

    private static String csrf(String page) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(page);
        assertTrue(matcher.find(), "the form should carry a CSRF token");
        return matcher.group(1);
    }

    private static int idOf(List<ContentEntry> entries, String name) {
        return entries.stream().filter(entry -> entry.values().get(0).equals(name))
                .findFirst().orElseThrow().id();
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
