package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Deleting a record is an event the audit log must show, in the same transaction as the delete. */
class SqliteContentServiceDeleteAuditTest {

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /** One facility, one agent at it, and one brief. Seed files are skipped. */
    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("missions.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService service =
                new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        service.add(ContentType.FACILITY, List.of("Langley Annex", "LGA"), admin);
        service.add(ContentType.AGENT,
                List.of("Cardinal", "1931-02-14", "1979-04-02", "Langley.", "1"), admin);
        service.add(ContentType.BRIEF, List.of("Old brief", "2026-01-01", "Some text."), admin);
        return service;
    }

    private List<AuditLogEntry> deletes(SqliteContentService service) throws Exception {
        return service.audit(admin).stream()
                .filter(entry -> entry.getEventType() == AuditEventType.DELETE).toList();
    }

    @Test
    void deletingARecordWritesOneDeleteRowNamingWhoAndWhat() throws Exception {
        SqliteContentService service = service();

        service.delete(ContentType.BRIEF, 1, admin);

        assertThrows(ProgramControlException.class, () -> service.read(ContentType.BRIEF, 1, admin));
        List<AuditLogEntry> deletes = deletes(service);
        assertEquals(1, deletes.size());
        assertEquals(AuditContentType.BRIEF, deletes.get(0).getContentType());
        assertEquals(1, deletes.get(0).getContentId());
        assertEquals("admin", deletes.get(0).getUsername());
    }

    @Test
    void deletingAMissingRecordFailsAndWritesNothing() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.BRIEF, 99, admin));

        assertTrue(deletes(service).isEmpty());
    }

    @Test
    void aDeleteBlockedByDependentRecordsWritesNothingAndKeepsTheRecord() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.FACILITY, 1, admin));

        assertTrue(deletes(service).isEmpty());
        assertEquals("Langley Annex", service.read(ContentType.FACILITY, 1, admin).values().get(0));
    }

    @Test
    void aStandardUserCannotDeleteAndNothingIsWritten() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.BRIEF, 1, reader));

        assertTrue(deletes(service).isEmpty());
        assertFalse(service.list(ContentType.BRIEF, admin).isEmpty());
    }
}