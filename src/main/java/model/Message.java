package model;


import java.time.LocalDateTime;
import java.util.Objects;

/** A message left for an agent. Readable by the user whose username matches the agent's name. */
public final class Message {
    private final int id;
    private final int agentId;
    private final String agentName;
    private final String sender;
    private final String body;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;

    public Message(int id, int agentId, String agentName, String sender,
                   String body, LocalDateTime createdAt) {
        this(id, agentId, agentName, sender, body, createdAt, null);
    }

    public Message(int id, int agentId, String agentName, String sender,
                   String body, LocalDateTime createdAt, LocalDateTime readAt) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (agentId < 1) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        this.id = id;
        this.agentId = agentId;
        this.agentName = requireText(agentName, "agentName");
        this.sender = requireText(sender, "sender");
        this.body = requireText(body, "body");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.readAt = readAt;
    }

    public int getId() { return id; }
    public int getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getSender() { return sender; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message)) {
            return false;
        }
        Message message = (Message) other;
        return id == message.id && agentId == message.agentId
                && agentName.equals(message.agentName) && sender.equals(message.sender)
                && body.equals(message.body) && createdAt.equals(message.createdAt)
                && Objects.equals(readAt, message.readAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, agentId, agentName, sender, body, createdAt, readAt);
    }

    @Override
    public String toString() {
        return "Message{id=" + id + ", agentId=" + agentId + ", agentName='" + agentName
                + "', sender='" + sender + "', createdAt=" + createdAt + '}';
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
