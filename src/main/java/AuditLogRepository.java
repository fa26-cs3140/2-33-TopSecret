import java.time.LocalDateTime;
import java.util.List;

/** Where the audit log is kept. The SQLite implementation sits behind this interface. */
public interface AuditLogRepository {

    /** Saves one entry. */
    void record(AuditLogEntry entry) throws AuditLogException;

    /** All entries, newest first. Never returns null. */
    List<AuditLogEntry> getAllEntries() throws AuditLogException;

    /** Convenience for callers: records an event that happened just now. */
    default void record(AuditEventType eventType, AuditContentType contentType, int contentId,
                        String username) throws AuditLogException {
        record(new AuditLogEntry(eventType, contentType, contentId, username, LocalDateTime.now()));
    }
}