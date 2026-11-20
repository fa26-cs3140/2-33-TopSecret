package model;

import java.time.Instant;
import java.util.Objects;

/**
 * A web session listed in the administrator-only terminal screen.
 * The id is the complete issued token, as required by Homework 7;
 * it must not be included in logs or web pages.
 */
public record WebSessionInfo(String id, String username, Instant createdAt, Instant lastSeen) {
    public WebSessionInfo {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(lastSeen, "lastSeen cannot be null");
    }
}