package model;

import controller.ProgramControlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SqliteMissionBriefCreationTest {
    @TempDir Path temporary;
    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);
    private final List<String> values = List.of("Gate report", "2026-11-23", "Watch the gate.");
    private Path database;
    private SqliteAuditLogRepository audit;
    private SqliteContentService service;

    @BeforeEach
    void initialize() throws Exception {
        database = temporary.resolve("briefs.db");
        new SqliteMissionRepository(mock(FileHandler.class), database, "missing.tsv").initialize();
        audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        service = new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        service.add(ContentType.FACILITY, List.of("North", "NO"), admin);
        service.add(ContentType.AGENT, List.of("Ghost", "1990-01-01", "", "", "1"), admin);
        service.addMission(List.of("Nightfall", "Operation", "2026-11-23"), 1, 1, admin);
    }

    @Test
    void createsLinkedBriefAndOneCreateAuditEntryPersistently() throws Exception {
        int briefId = service.addBrief(values, 1, admin);
        SqliteContentService reopened = new SqliteContentService(database, audit, mock(FileHandler.class));
        assertEquals(values, reopened.read(ContentType.BRIEF, briefId, reader).values());
        assertEquals(1, reopened.missionOf(briefId, reader).orElseThrow().id());
        assertEquals(List.of(new ContentEntry(briefId, values)), reopened.briefsFor(1, reader));
        assertEquals(1, audit.getAllEntries().stream().filter(entry ->
                entry.getContentType() == AuditContentType.BRIEF
                        && entry.getEventType() == AuditEventType.CREATE
                        && entry.getContentId() == briefId
                        && entry.getUsername().equals("admin")).count());
        assertFalse(audit.getAllEntries().stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.BRIEF && entry.getEventType() == AuditEventType.UPDATE));
    }

    @Test
    void rejectsReadersMissingMissionsAndInvalidFieldsWithoutOrphans() throws Exception {
        assertThrows(ProgramControlException.class, () -> service.addBrief(values, 1, reader));
        assertThrows(ProgramControlException.class, () -> service.addBrief(values, 1, null));
        for (int missionId : List.of(-1, 0, 999)) {
            assertThrows(ProgramControlException.class, () -> service.addBrief(values, missionId, admin));
        }
        assertThrows(ProgramControlException.class, () -> service.addBrief(List.of("Short"), 1, admin));
        assertTrue(service.list(ContentType.BRIEF, admin).isEmpty());
    }

    @Test
    void auditFailureRollsBackBriefAndMissionLink() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_brief BEFORE INSERT ON audit_log "
                    + "WHEN NEW.content_type = 'BRIEF' AND NEW.event_type = 'CREATE' "
                    + "BEGIN SELECT RAISE(ABORT, 'private failure'); END");
        }
        assertThrows(ProgramControlException.class, () -> service.addBrief(values, 1, admin));
        assertTrue(service.briefsFor(1, admin).isEmpty());
        assertTrue(service.list(ContentType.BRIEF, admin).isEmpty());
    }
}
