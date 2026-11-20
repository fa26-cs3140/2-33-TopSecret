package controller;

import model.SessionAdministration;
import model.WebSessionInfo;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Web sessions kept in the same SQLite file as the content. The web server
 * and the terminal are separate programs, so an administrator in the terminal
 * can only see and end the web server's sessions if they are stored rather
 * than held in memory.
 *
 * A session is valid while no more than {@link #TIMEOUT} has passed since its
 * last request. Every successful lookup moves that moment forward, so only
 * inactivity ends a session, never the total time since login.
 */
public final class SqliteWebSessionStore implements WebSessionStore, SessionAdministration {

    public static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Path database;
    private final Clock clock;
    private final Duration timeout;
    private final SecureRandom random = new SecureRandom();

    public SqliteWebSessionStore(Path database) {
        this(database, Clock.systemUTC(), TIMEOUT);
    }

    public SqliteWebSessionStore(Path database, Clock clock, Duration timeout) {
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
    }

    /** Creates the table when it is missing. Safe to call from both programs. */
    public void initialize() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS web_sessions ("
                    + "token TEXT PRIMARY KEY, username TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, last_seen INTEGER NOT NULL)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Web sessions could not be initialized.", exception);
        }
    }

    @Override
    public String create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A session requires a username.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = clock.millis();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO web_sessions (token, username, created_at, last_seen) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, token);
            statement.setString(2, username);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
            return token;
        } catch (SQLException exception) {
            throw new IllegalStateException("The session could not be created.", exception);
        }
    }

    @Override
    public Optional<String> usernameFor(String token) {
        if (token == null) {
            return Optional.empty();
        }
        long now = clock.millis();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            String username;
            long lastSeen;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT username, last_seen FROM web_sessions WHERE token = ?")) {
                statement.setString(1, token);
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        return Optional.empty();
                    }
                    username = results.getString(1);
                    lastSeen = results.getLong(2);
                }
            }
            if (now - lastSeen > timeout.toMillis()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM web_sessions WHERE token = ?")) {
                    statement.setString(1, token);
                    statement.executeUpdate();
                }
                connection.commit();
                return Optional.empty();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE web_sessions SET last_seen = ? WHERE token = ?")) {
                statement.setLong(1, now);
                statement.setString(2, token);
                statement.executeUpdate();
            }
            connection.commit();
            return Optional.of(username);
        } catch (SQLException exception) {
            throw new IllegalStateException("The session could not be read.", exception);
        }
    }

    @Override
    public void invalidate(String token) {
        if (token == null) {
            return;
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM web_sessions WHERE token = ?")) {
            statement.setString(1, token);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("The session could not be ended.", exception);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM web_sessions");
        } catch (SQLException exception) {
            throw new IllegalStateException("The sessions could not be cleared.", exception);
        }
    }

    @Override
    public List<WebSessionInfo> activeSessions() {
        long now = clock.millis();
        try (Connection connection = connect()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM web_sessions WHERE ? - last_seen > ?")) {
                statement.setLong(1, now);
                statement.setLong(2, timeout.toMillis());
                statement.executeUpdate();
            }
            List<WebSessionInfo> sessions = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery(
                         "SELECT token, username, created_at, last_seen FROM web_sessions "
                                 + "ORDER BY created_at, token")) {
                while (results.next()) {
                    sessions.add(new WebSessionInfo(results.getString(1),
                            results.getString(2), Instant.ofEpochMilli(results.getLong(3)),
                            Instant.ofEpochMilli(results.getLong(4))));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new IllegalStateException("The sessions could not be listed.", exception);
        }
    }

    @Override
    public boolean revokeSession(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        // Match the complete issued token, never a prefix or a SQL wildcard.
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM web_sessions WHERE token = ?")) {
            statement.setString(1, token);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("The session could not be revoked.", exception);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            // two programs share this file, so wait for the other one instead of failing at once
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}