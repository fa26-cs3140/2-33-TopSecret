import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAuditLogRepositoryTest {
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 10, 20, 12, 0, 0);
    private static final LocalDateTime SECOND = LocalDateTime.of(2026, 10, 20, 12, 5, 30);

    @TempDir
    Path folder;

    private SqliteAuditLogRepository repository() {
        return new SqliteAuditLogRepository(folder.resolve("test.db"));
    }

    @Test
    void defaultConstructorCreatesRepository() {
        assertNotNull(new SqliteAuditLogRepository());
    }

    @Test
    void constructorRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> new SqliteAuditLogRepository(null));
    }

    @Test
    void initializeCreatesMissingFolder() throws AuditLogException {
        Path nested = folder.resolve("a").resolve("b").resolve("audit.db");

        new SqliteAuditLogRepository(nested).initialize();

        assertTrue(Files.isDirectory(folder.resolve("a").resolve("b")));
    }

    @Test
    void emptyLogReturnsEmptyList() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();

        assertTrue(repository.getAllEntries().isEmpty());
    }

    @Test
    void recordStoresEveryField() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();

        repository.record(new AuditLogEntry(AuditEventType.CREATE, AuditContentType.AGENT, 9, "agent", FIRST));

        AuditLogEntry saved = repository.getAllEntries().get(0);
        assertEquals(1, saved.getId());
        assertEquals(AuditEventType.CREATE, saved.getEventType());
        assertEquals(AuditContentType.AGENT, saved.getContentType());
        assertEquals(9, saved.getContentId());
        assertEquals("agent", saved.getUsername());
        assertEquals(FIRST, saved.getTimestamp());
    }

    @Test
    void entriesComeBackNewestFirst() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();
        repository.record(new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "agent", FIRST));
        repository.record(new AuditLogEntry(AuditEventType.CREATE, AuditContentType.FACILITY, 2, "bob", SECOND));

        List<AuditLogEntry> entries = repository.getAllEntries();

        assertEquals(2, entries.size());
        assertEquals(2, entries.get(0).getContentId());
        assertEquals(1, entries.get(1).getContentId());
    }

    @Test
    void initializeTwiceKeepsExistingEntries() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();
        repository.record(new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "agent", FIRST));

        repository.initialize();

        assertEquals(1, repository.getAllEntries().size());
    }

    @Test
    void recordWithoutEntryTimestampUsesCurrentTime() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        repository.record(AuditEventType.READ, AuditContentType.BRIEF, 5, "agent");

        LocalDateTime saved = repository.getAllEntries().get(0).getTimestamp();
        assertFalse(saved.isBefore(before));
        assertFalse(saved.isAfter(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void recordRejectsNullEntry() throws AuditLogException {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();

        assertThrows(NullPointerException.class, () -> repository.record(null));
    }

    @Test
    void recordFailsBeforeTableExists() {
        SqliteAuditLogRepository repository = repository();

        assertThrows(AuditLogException.class, () -> repository.record(
                new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "agent", FIRST)));
    }

    @Test
    void readFailsBeforeTableExists() {
        assertThrows(AuditLogException.class, () -> repository().getAllEntries());
    }

    @Test
    void unknownEventTypeInDatabaseIsReportedAsInvalidEntry() throws Exception {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();
        insertRaw("BOGUS", "2026-10-20T12:00:00");

        assertThrows(AuditLogException.class, repository::getAllEntries);
    }

    @Test
    void badTimestampInDatabaseIsReportedAsInvalidEntry() throws Exception {
        SqliteAuditLogRepository repository = repository();
        repository.initialize();
        insertRaw("READ", "not a time");

        assertThrows(AuditLogException.class, repository::getAllEntries);
    }

    private void insertRaw(String eventType, String timestamp) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("test.db"));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO audit_log "
                    + "(event_type, content_type, content_id, username, timestamp) VALUES ('"
                    + eventType + "', 'BRIEF', 1, 'agent', '" + timestamp + "')");
        }
    }
}