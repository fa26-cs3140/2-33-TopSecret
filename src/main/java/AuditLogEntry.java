import java.time.LocalDateTime;
import java.util.Objects;

public class AuditLogEntry {
    private final int id;
    private final AuditEventType eventType;
    private final AuditContentType contentType;
    private final int contentId;
    private final String username;
    private final LocalDateTime timestamp;

    // a new entry that has not been saved yet; the database assigns the id
    public AuditLogEntry(AuditEventType eventType, AuditContentType contentType, int contentId,
                         String username, LocalDateTime timestamp) {
        this(0, eventType, contentType, contentId, username, timestamp);
    }

    // an entry read back from the database
    public AuditLogEntry(int id, AuditEventType eventType, AuditContentType contentType, int contentId,
                         String username, LocalDateTime timestamp) {
        this.id = id;
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType cannot be null");
        this.contentId = contentId;
        this.username = Objects.requireNonNull(username, "username cannot be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public int getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public AuditContentType getContentType() {
        return contentType;
    }

    public int getContentId() {
        return contentId;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}