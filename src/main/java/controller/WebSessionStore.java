package controller;

import java.util.Optional;

public interface WebSessionStore {
    String create(String username);

    Optional<String> usernameFor(String token);

    void invalidate(String token);
}
