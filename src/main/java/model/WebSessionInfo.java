package model;

import java.time.Instant;
import java.util.Objects;

/**
 * What an administrator may see of a web session. The id is only the first
 * characters of the secret token, enough to pick a session out of a list but
 * useless for taking it over.
 */
public record WebSessionInfo(String id, String username, Instant createdAt, Instant lastSeen) {
    public WebSessionInfo {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(lastSeen, "lastSeen cannot be null");
    }
}