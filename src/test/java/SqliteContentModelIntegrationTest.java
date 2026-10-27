import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqliteContentModelIntegrationTest {
    private static final String FACILITIES = "Blackwood Annex\tBA\nRose Garden\tRG\n";
    private static final String AGENTS = "Vanguard\tJune 14, 1985\nGhost\tNovember 22, 1992\n"
            + "Mirage\tMarch 03, 1988\n";

    @TempDir Path temporary;
    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    @Test
    void seedFilesPopulateModelsAndEveryMissionRelationshipExactlyOnce() throws Exception {
        Path database = temporary.resolve("content.db");
        createLegacyMissions(database, 3);
        SqliteContentService service = service(database, FACILITIES, AGENTS);

        service.initialize();
        service.initialize();

        assertEquals(2, count(database, "facilities"));
        assertEquals(3, count(database, "agents"));
        assertEquals(3, count(database, "mission_facilities"));
        assertEquals(3, count(database, "mission_agents"));
        assertEquals(List.of("1:1", "2:2", "3:1"), pairs(database,
                "SELECT mission_id, facility_id FROM mission_facilities ORDER BY mission_id"));
        assertEquals(List.of("1:1", "2:2", "3:3"), pairs(database,
                "SELECT mission_id, agent_id FROM mission_agents ORDER BY mission_id, agent_id"));
        assertEquals(List.of("1:1", "2:2", "3:1"), pairs(database,
                "SELECT id, facility_id FROM agents ORDER BY id"));

        assertEquals(List.of("Blackwood Annex", "BA"),
                service.read(ContentType.FACILITY, 1, reader).values());
        assertEquals(List.of("Vanguard", "1985-06-14", "", "", "1"),
                service.read(ContentType.AGENT, 1, reader).values());
    }

    @Test
    void malformedAgentSeedRollsBackFacilitiesAgentsAndRelationshipsTogether() throws Exception {
        Path database = temporary.resolve("malformed.db");
        createLegacyMissions(database, 1);
        SqliteContentService service = service(database, FACILITIES, "Vanguard\tnot-a-date\n");

        assertThrows(ProgramControlException.class, service::initialize);

        assertEquals(0, count(database, "facilities"));
        assertEquals(0, count(database, "agents"));
        assertEquals(0, count(database, "mission_facilities"));
        assertEquals(0, count(database, "mission_agents"));
    }

    @Test
    void missingSeedFilesStillAllowAnEmptyDatabaseToStart() throws Exception {
        Path database = temporary.resolve("missing.db");
        SqliteContentService service = service(database, null, null);

        service.initialize();

        assertTrue(service.list(ContentType.FACILITY, reader).isEmpty());
        assertTrue(service.list(ContentType.AGENT, reader).isEmpty());
        assertTrue(service.list(ContentType.BRIEF, reader).isEmpty());
    }

    @Test
    void projectSeedFilesImportEveryProvidedFacilityAndAgent() throws Exception {
        Path database = temporary.resolve("project-seeds.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService service = new SqliteContentService(database, audit);

        service.initialize();

        assertEquals(6, count(database, "facilities"));
        assertEquals(10, count(database, "agents"));
    }

    @Test
    void foreignKeysAndTriggersPreventEveryOrphaningMutation() throws Exception {
        Path database = temporary.resolve("constraints.db");
        createLegacyMissions(database, 2);
        SqliteContentService service = service(database, FACILITIES, AGENTS);
        service.initialize();

        try (Connection connection = connect(database); var statement = connection.createStatement()) {
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("DELETE FROM facilities WHERE id = 1"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("DELETE FROM agents WHERE id = 1"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("DELETE FROM mission_facilities WHERE mission_id = 1"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("DELETE FROM mission_agents WHERE mission_id = 1"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO mission_agents (mission_id, agent_id) VALUES (1, 999)"));

            // Deleting a whole mission is valid; its relationship rows should
            // disappear with it instead of becoming orphaned.
            // Xerial reports the parent row plus rows removed by foreign-key
            // cascades, so only require that the parent deletion succeeded.
            assertTrue(statement.executeUpdate("DELETE FROM missions WHERE id = 1") >= 1);
        }

        assertEquals(1, count(database, "missions"));
        assertEquals(1, count(database, "mission_facilities"));
        assertEquals(1, count(database, "mission_agents"));
    }

    @Test
    void adminsCanUpdateAndDeleteContentWhileStandardUsersCannot() throws Exception {
        Path database = temporary.resolve("mutations.db");
        SqliteContentService service = service(database, null, null);
        service.initialize();
        service.add(ContentType.FACILITY, List.of("Headquarters", "HQ"), admin);
        service.add(ContentType.BRIEF, List.of("Draft", "2026-10-22", "First text"), admin);

        service.update(ContentType.FACILITY, 1, List.of("Field Office", "FO"), admin);
        assertEquals(List.of("Field Office", "FO"),
                service.read(ContentType.FACILITY, 1, reader).values());

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getEventType() == AuditEventType.UPDATE
                        && entry.getContentType() == AuditContentType.FACILITY
                        && entry.getContentId() == 1
                        && entry.getUsername().equals("admin")));

        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.FACILITY, 1, List.of("Denied", "NO"), reader));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.BRIEF, 1, reader));
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.BRIEF, 1,
                        List.of("Draft", "not-a-date", "Text"), admin));

        service.delete(ContentType.BRIEF, 1, admin);
        assertThrows(ProgramControlException.class,
                () -> service.read(ContentType.BRIEF, 1, reader));
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.BRIEF, 99,
                        List.of("Missing", "2026-10-22", "Nothing"), admin));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.BRIEF, 99, admin));
    }

    @Test
    void briefSeedFileImportsEveryRowOnceAndSkipsTheHeader() throws Exception {
        Path database = temporary.resolve("briefs.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        when(files.readText("mission_briefs.tsv")).thenReturn(
                "Title\tDate\tText\r\nOne\t1970-11-03\tFirst\r\nTwo\t1972-09-15\tSecond\r\n");
        SqliteContentService service = new SqliteContentService(database, audit, files);

        service.initialize();
        service.initialize();   // second start must not duplicate

        List<ContentEntry> briefs = service.list(ContentType.BRIEF, reader);
        assertEquals(2, briefs.size());
        assertEquals(List.of("One", "1970-11-03", "First"), briefs.get(0).values());
    }

    @Test
    void malformedBriefSeedRowsAreRejected() throws Exception {
        for (String bad : List.of("T\tnot-a-date\tX\n", "T\t1970-01-01\t\n", "only\ttwo\n")) {
            Path database = temporary.resolve("bad" + Math.abs(bad.hashCode()) + ".db");
            SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
            audit.initialize();
            FileHandler files = mock(FileHandler.class);
            when(files.readText("mission_briefs.tsv")).thenReturn(bad);
            SqliteContentService service = new SqliteContentService(database, audit, files);
            assertThrows(ProgramControlException.class, () -> service.initialize());
        }
    }

    @Test
    void deletingAReferencedFacilityOrAgentThroughTheServiceIsRejected() throws Exception {
        Path database = temporary.resolve("protected.db");
        createLegacyMissions(database, 1);
        SqliteContentService service = service(database, FACILITIES, AGENTS);
        service.initialize();

        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.FACILITY, 1, admin));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.AGENT, 1, admin));
        assertEquals(2, count(database, "facilities"));
        assertEquals(3, count(database, "agents"));
    }

    private SqliteContentService service(Path database, String facilities, String agents) throws Exception {
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        when(files.readText("facilities.tsv")).thenReturn(facilities);
        when(files.readText("agents.tsv")).thenReturn(agents);
        return new SqliteContentService(database, audit, files);
    }

    private static void createLegacyMissions(Path database, int amount) throws Exception {
        try (Connection connection = connect(database); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE missions (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "title TEXT NOT NULL, brief TEXT NOT NULL, date TEXT NOT NULL)");
            for (int index = 1; index <= amount; index++) {
                statement.executeUpdate("INSERT INTO missions (title, brief, date) VALUES "
                        + "('Mission " + index + "', 'Brief " + index + "', '2000-01-01')");
            }
        }
    }

    private static Connection connect(Path database) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static int count(Path database, String table) throws Exception {
        try (Connection connection = connect(database);
             var statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    private static List<String> pairs(Path database, String query) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection connection = connect(database);
             var statement = connection.createStatement();
             ResultSet results = statement.executeQuery(query)) {
            while (results.next()) {
                rows.add(results.getInt(1) + ":" + results.getInt(2));
            }
        }
        return rows;
    }
}
