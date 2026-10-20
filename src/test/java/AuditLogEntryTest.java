import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditLogEntryTest {
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 20, 12, 0, 0);

    @Test
    void fullConstructorStoresEveryField() {
        AuditLogEntry entry = new AuditLogEntry(7, AuditEventType.READ, AuditContentType.AGENT, 3, "agent", WHEN);

        assertEquals(7, entry.getId());
        assertEquals(AuditEventType.READ, entry.getEventType());
        assertEquals(AuditContentType.AGENT, entry.getContentType());
        assertEquals(3, entry.getContentId());
        assertEquals("agent", entry.getUsername());
        assertEquals(WHEN, entry.getTimestamp());
    }

    @Test
    void newEntryConstructorLeavesIdAtZero() {
        AuditLogEntry entry = new AuditLogEntry(AuditEventType.CREATE, AuditContentType.BRIEF, 12, "bob", WHEN);

        assertEquals(0, entry.getId());
        assertEquals(AuditEventType.CREATE, entry.getEventType());
        assertEquals(AuditContentType.BRIEF, entry.getContentType());
        assertEquals(12, entry.getContentId());
        assertEquals("bob", entry.getUsername());
        assertEquals(WHEN, entry.getTimestamp());
    }

    @Test
    void nullEventTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuditLogEntry(null, AuditContentType.BRIEF, 1, "agent", WHEN));
    }

    @Test
    void nullContentTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuditLogEntry(AuditEventType.READ, null, 1, "agent", WHEN));
    }

    @Test
    void nullUsernameIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, null, WHEN));
    }

    @Test
    void blankUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "  ", WHEN));
    }

    @Test
    void nullTimestampIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "agent", null));
    }
}