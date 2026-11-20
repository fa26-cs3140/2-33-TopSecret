package controller;

import model.SessionAdministration;
import model.WebSessionInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Web sessions kept in SQLite so the web server and the terminal share them. */
public final class SqliteWebSessionStore implements WebSessionStore, SessionAdministration {

    public static final Duration TIMEOUT = Duration.ofMinutes(30);

    public SqliteWebSessionStore(Path database) {
        this(database, Clock.systemUTC(), TIMEOUT);
    }

    public SqliteWebSessionStore(Path database, Clock clock, Duration timeout) {
    }

    public void initialize() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public String create(String username) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Optional<String> usernameFor(String token) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void invalidate(String token) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public List<WebSessionInfo> activeSessions() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean revokeSession(String id) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}