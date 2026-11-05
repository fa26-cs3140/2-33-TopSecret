package controller;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryWebSessionStore implements WebSessionStore {
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A session requires a username.");
        }
        byte[] bytes = new byte[32];
        String token;
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessions.putIfAbsent(token, username) != null);
        return token;
    }

    @Override
    public Optional<String> usernameFor(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessions.get(token));
    }

    @Override
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    @Override
    public void clear() {
        sessions.clear();
    }
}
