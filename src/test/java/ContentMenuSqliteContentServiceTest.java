import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ContentMenuSqliteContentServiceTest {
    @TempDir Path temporary;
    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("nested/content.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        // These tests exercise interactive creation, so leave the optional
        // seed files absent and start each temporary database empty.
        SqliteContentService service = new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        return service;
    }

    @Test
    void allTypesCanBeCreatedListedReadAndAuditedAcrossRestart() throws Exception {
        SqliteContentService service = service();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ContentMenu(service).run(new Scanner("3\n3\nHeadquarters\nHQ\n2\n1\n4\n"
                + "2\n3\nAgent B\n1990-01-01\n\nNotes\n1\n2\n1\n4\n"
                + "1\n3\nReport\n2026-10-22\nBrief text\n2\n1\n4\n5\n4\n"), new PrintStream(buffer), admin);
        assertTrue(buffer.toString().contains("text: Brief text"));
        assertTrue(buffer.toString().contains("date_of_death: "));
        SqliteContentService reopened = service();
        for (ContentType type : ContentType.values()) {
            assertEquals(1, reopened.list(type, reader).size());
            assertEquals(1, reopened.read(type, 1, reader).id());
        }
        assertEquals(3, reopened.audit(admin).stream().filter(entry -> entry.getEventType() == AuditEventType.CREATE).count());
        assertTrue(reopened.audit(admin).stream().anyMatch(entry -> entry.getUsername().equals("reader")));
    }

    @Test
    void interactiveUpdatePersistsAcrossRestart() throws Exception {
        SqliteContentService service = service();
        service.add(ContentType.FACILITY, List.of("Headquarters", "HQ"), admin);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        new ContentMenu(service).run(
                new Scanner("3\n5\n1\nField Office\nFO\n4\n4\n"),
                new PrintStream(buffer), admin);

        SqliteContentService reopened = service();
        assertEquals(List.of("Field Office", "FO"),
                reopened.read(ContentType.FACILITY, 1, reader).values());
        assertTrue(buffer.toString().contains("Entry updated."));
        assertTrue(buffer.toString().contains("1. Field Office"));
    }

    @Test
    void accessAndValidationAreEnforcedBelowTheMenu() throws Exception {
        SqliteContentService service = service();
        assertThrows(ProgramControlException.class, () -> service.list(ContentType.BRIEF, null));
        assertThrows(ProgramControlException.class, () -> service.read(ContentType.BRIEF, 1, null));
        assertThrows(ProgramControlException.class, () -> service.audit(reader));
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.FACILITY, List.of("HQ", "HQ"), reader));
        assertThrows(ProgramControlException.class, () -> service.read(ContentType.BRIEF, 99, admin));
        for (List<String> values : List.of(List.of("x"), List.of("", "bad", "text"), List.of("title", "bad", "text"))) {
            assertThrows(ProgramControlException.class, () -> service.add(ContentType.BRIEF, values, admin));
        }
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.AGENT, List.of("Agent", "2000-01-01", "1990-01-01", "", "1"), admin));
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.AGENT, List.of("Agent", "2000-01-01", "", "", "0"), admin));
        assertTrue(service.audit(admin).isEmpty());
    }

    @Test
    void orphanedAgentsAreRejectedAndFacilityDeletionIsRestricted() throws Exception {
        SqliteContentService service = service();
        List<String> agent = List.of("Agent", "2000-01-01", "", "", "1");
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.AGENT, agent, admin));
        assertTrue(service.audit(admin).isEmpty());
        service.add(ContentType.FACILITY, List.of("HQ", "HQ"), admin);
        service.add(ContentType.AGENT, agent, admin);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("nested/content.db"));
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("DELETE FROM facilities WHERE id = 1"));
        }
        assertEquals(1, service.list(ContentType.FACILITY, reader).size());
    }

    @Test
    void failedAuditRollsBackContentCreation() throws Exception {
        SqliteContentService service = service();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("nested/content.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE audit_log");
        }
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.FACILITY, List.of("HQ", "HQ"), admin));
        assertTrue(service.list(ContentType.FACILITY, reader).isEmpty());
        assertThrows(ProgramControlException.class, () -> service.audit(admin));
    }

    @Test
    void failedAuditRollsBackUpdate() throws Exception {
        SqliteContentService service = service();
        service.add(ContentType.FACILITY, List.of("HQ", "HQ"), admin);
        String url = "jdbc:sqlite:" + temporary.resolve("nested/content.db");
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE audit_log");
        }
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.FACILITY, 1, List.of("New", "NW"), admin));
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT name FROM facilities WHERE id = 1")) {
            assertTrue(results.next());
            assertEquals("HQ", results.getString(1));
        }
    }

    @Test
    void entryCopiesValuesAndExposesRecordMethods() {
        var values = new java.util.ArrayList<>(List.of("HQ", "HQ"));
        ContentEntry entry = new ContentEntry(1, values);
        values.clear();
        assertEquals(List.of("HQ", "HQ"), entry.values());
        assertEquals(new ContentEntry(1, List.of("HQ", "HQ")), entry);
        assertEquals(new ContentEntry(1, List.of("HQ", "HQ")).hashCode(), entry.hashCode());
        assertTrue(entry.toString().contains("HQ"));
        assertEquals(ContentType.BRIEF, ContentType.valueOf("BRIEF"));
    }
}
