package model;

import controller.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebEditingSqliteTest {
    @TempDir Path temporary;
    private final Session admin = new Session("admin", Role.ADMIN);
    private final HttpClient client = HttpClient.newHttpClient();
    private SqliteContentService service;
    private SqliteAuditLogRepository audit;
    private HttpWebServer server;
    private Path database;
    private String base;
    private String cookie;

    @BeforeEach
    void start() throws Exception {
        database = temporary.resolve("editing.db");
        FileHandler files = mock(FileHandler.class);
        new SqliteMissionRepository(files, database, "missing.tsv").initialize();
        audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        service = new SqliteContentService(database, audit, files);
        service.initialize();
        AuthenticationService accounts = mock(AuthenticationService.class);
        when(accounts.sessionFor("admin")).thenReturn(admin);
        WebAuthentication login = mock(WebAuthentication.class);
        when(login.hasAccounts()).thenReturn(true);
        InMemoryWebSessionStore sessions = new InMemoryWebSessionStore();
        cookie = WebServerController.SESSION_COOKIE + "=" + sessions.create("admin");
        server = new HttpWebServer(login, sessions, new ContentController(service, accounts),
                new InetSocketAddress("localhost", 0));
        server.start();
        base = "http://localhost:" + server.address().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void everyTypeCreatesAndUpdatesWithExactlyOneAuditEventAndPersistentValues() throws Exception {
        create(ContentType.FACILITY, List.of("North", "NO"), Map.of());
        create(ContentType.AGENT, List.of("Ghost", "1990-01-01", "", "", "1"), Map.of());
        create(ContentType.BRIEF, List.of("Signal", "2026-11-19", "Details\nSecond line"), Map.of());
        create(ContentType.MISSION, List.of("Nightfall", "Operation", "2026-11-19"),
                Map.of("facility_id", "1", "agent_id", "1"));
        create(ContentType.COUNTRY, List.of("France"), Map.of());
        assertEquals(1, service.facilityFor(1, admin).orElseThrow().id());
        assertEquals(1, service.agentsFor(1, admin).getFirst().id());
        for (ContentType type : ContentType.values()) {
            ContentEntry original = service.read(type, 1, admin);
            String page = get("/edit?type=" + type.table + "&id=1").body();
            assertTrue(page.contains(original.values().getFirst()));
            List<String> updated = new ArrayList<>(original.values());
            updated.set(0, "Updated " + type.table);
            HttpResponse<String> response = save("/update", type, updated, Map.of("id", "1"));
            assertEquals(303, response.statusCode());
            assertEquals("/record?type=" + type.table + "&id=1", response.headers().firstValue("Location").orElseThrow());
            assertEquals(updated, service.read(type, 1, admin).values());
            for (AuditEventType event : List.of(AuditEventType.CREATE, AuditEventType.UPDATE)) {
                assertEquals(1, audit.getAllEntries().stream().filter(entry -> entry.getEventType() == event
                        && entry.getContentType().name().equals(type.name()) && entry.getUsername().equals("admin")).count());
            }
        }
        SqliteContentService reopened = new SqliteContentService(database, audit, mock(FileHandler.class));
        assertEquals("Updated agents", reopened.read(ContentType.AGENT, 1, admin).values().getFirst());
    }

    @Test
    void invalidDatesAndRelationshipsRetainInputAndNeverSavePartialRecords() throws Exception {
        create(ContentType.FACILITY, List.of("North", "NO"), Map.of());
        create(ContentType.AGENT, List.of("Ghost", "1990-01-01", "", "", "1"), Map.of());
        for (String facility : List.of("", "0", "999", "abc")) {
            HttpResponse<String> invalid = save("/create", ContentType.MISSION,
                    List.of("<Unstored>", "Details", "2026-11-19"), Map.of("facility_id", facility, "agent_id", "1"));
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("&lt;Unstored&gt;"));
        }
        HttpResponse<String> missingAgent = save("/create", ContentType.MISSION,
                List.of("Unstored", "Details", "2026-11-19"), Map.of("facility_id", "1", "agent_id", "999"));
        assertEquals(400, missingAgent.statusCode());
        assertTrue(missingAgent.body().contains("value=\"1\" selected"));
        assertEquals(400, save("/create", ContentType.BRIEF, List.of("Bad date", "2026-02-30", "Text"), Map.of()).statusCode());
        assertEquals(400, save("/update", ContentType.AGENT,
                List.of("Ghost", "1990-01-01", "1989-12-31", "Notes", "1"), Map.of("id", "1")).statusCode());
        assertEquals(400, save("/update", ContentType.AGENT,
                List.of("Ghost", "1990-01-01", "", "Notes", "999"), Map.of("id", "1")).statusCode());
        assertEquals(List.of("Ghost", "1990-01-01", "", "", "1"), service.read(ContentType.AGENT, 1, admin).values());
        assertTrue(service.list(ContentType.MISSION, admin).isEmpty());
        assertTrue(service.list(ContentType.BRIEF, admin).isEmpty());
        assertEquals(2, audit.getAllEntries().stream().filter(entry -> entry.getEventType() == AuditEventType.CREATE).count());
        assertFalse(audit.getAllEntries().stream().anyMatch(entry -> entry.getEventType() == AuditEventType.UPDATE));
    }

    @Test
    void auditFailureRollsBackOrdinaryWritesAndMissionRelationships() throws Exception {
        create(ContentType.FACILITY, List.of("North", "NO"), Map.of());
        create(ContentType.AGENT, List.of("Ghost", "1990-01-01", "", "", "1"), Map.of());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_writes BEFORE INSERT ON audit_log "
                    + "WHEN NEW.event_type IN ('CREATE', 'UPDATE') BEGIN SELECT RAISE(ABORT, 'private audit failure'); END");
        }
        assertEquals(500, save("/create", ContentType.BRIEF, List.of("Unstored", "2026-11-19", "Text"), Map.of()).statusCode());
        HttpResponse<String> mission = save("/create", ContentType.MISSION,
                List.of("Unstored", "Text", "2026-11-19"), Map.of("facility_id", "1", "agent_id", "1"));
        assertEquals(500, mission.statusCode());
        assertFalse(mission.body().contains("private audit failure"));
        assertEquals(500, save("/update", ContentType.FACILITY, List.of("Unstored", "NO"), Map.of("id", "1")).statusCode());
        assertEquals("North", service.read(ContentType.FACILITY, 1, admin).values().getFirst());
        assertTrue(service.list(ContentType.BRIEF, admin).isEmpty());
        assertTrue(service.list(ContentType.MISSION, admin).isEmpty());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            for (String table : List.of("mission_facilities", "mission_agents")) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
            }
        }
    }

    private void create(ContentType type, List<String> values, Map<String, String> relationships) throws Exception {
        HttpResponse<String> response = save("/create", type, values, relationships);
        assertEquals(303, response.statusCode(), response.body());
        assertEquals("/content?type=" + type.table, response.headers().firstValue("Location").orElseThrow());
        assertEquals(values, service.read(type, 1, admin).values());
    }

    private HttpResponse<String> save(String path, ContentType type, List<String> values, Map<String, String> extras)
            throws Exception {
        String page = get("/new?type=" + type.table).body();
        var matcher = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(page);
        assertTrue(matcher.find());
        Map<String, String> fields = new LinkedHashMap<>(extras);
        fields.put("csrf", matcher.group(1));
        fields.put("type", type.table);
        for (int index = 0; index < values.size(); index++) {
            fields.put(type.fields.get(index), values.get(index));
        }
        List<String> encoded = new ArrayList<>();
        fields.forEach((key, value) -> encoded.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", encoded))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
