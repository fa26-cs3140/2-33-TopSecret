package controller;

import model.MutableClock;
import model.WebSessionInfo;
import model.AuditLogRepository;
import model.AuditEventType;
import model.AuditContentType;
import model.Role;
import model.Session;
import model.SessionAdmin;
import view.SessionMenu;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteWebSessionStoreTest {

    @TempDir Path temporary;

    private final MutableClock clock = new MutableClock();
    private Path database;
    private SqliteWebSessionStore store;

    @BeforeEach
    void setUp() {
        database = temporary.resolve("missions.db");
        store = new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
        store.initialize();
    }

    @Test
    void aCreatedSessionResolvesToItsUser() {
        String token = store.create("alice");

        assertEquals(Optional.of("alice"), store.usernameFor(token));
        assertTrue(token.length() >= 43, "a 256-bit token");
    }

    @Test
    void everyTokenIsDifferent() {
        assertNotEquals(store.create("alice"), store.create("alice"));
    }

    @Test
    void unknownBlankAndMissingTokensResolveToNothing() {
        assertEquals(Optional.empty(), store.usernameFor("nonsense"));
        assertEquals(Optional.empty(), store.usernameFor(""));
        assertEquals(Optional.empty(), store.usernameFor(null));
    }

    @Test
    void aSessionNeedsAUsername() {
        assertThrows(IllegalArgumentException.class, () -> store.create(null));
        assertThrows(IllegalArgumentException.class, () -> store.create("  "));
    }

    @Test
    void invalidateEndsOnlyThatSessionAndToleratesNull() {
        String first = store.create("alice");
        String second = store.create("bob");

        store.invalidate(first);
        store.invalidate(null);

        assertEquals(Optional.empty(), store.usernameFor(first));
        assertEquals(Optional.of("bob"), store.usernameFor(second));
    }

    @Test
    void clearEndsEverySession() {
        String token = store.create("alice");

        store.clear();

        assertEquals(Optional.empty(), store.usernameFor(token));
        assertTrue(store.activeSessions().isEmpty());
    }

    @Test
    void aSessionIdleForMoreThanThirtyMinutesIsGone() {
        String token = store.create("alice");

        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        assertEquals(Optional.empty(), store.usernameFor(token));
    }

    @Test
    void aSessionIdleForExactlyThirtyMinutesIsStillValid() {
        String token = store.create("alice");

        clock.advance(Duration.ofMinutes(30));

        assertEquals(Optional.of("alice"), store.usernameFor(token));
    }

    @Test
    void everyRequestRestartsTheThirtyMinuteWindow() {
        String token = store.create("alice");

        clock.advance(Duration.ofMinutes(20));
        assertEquals(Optional.of("alice"), store.usernameFor(token));
        clock.advance(Duration.ofMinutes(20));
        assertEquals(Optional.of("alice"), store.usernameFor(token));
        clock.advance(Duration.ofMinutes(31));
        assertEquals(Optional.empty(), store.usernameFor(token));
    }

    @Test
    void anExpiredSessionIsRemovedFromTheTable() {
        store.create("alice");
        store.create("bob");

        clock.advance(Duration.ofMinutes(31));

        assertTrue(store.activeSessions().isEmpty());
    }

    @Test
    void anotherProgramOpeningTheSameDatabaseSeesTheSession() {
        String token = store.create("alice");

        SqliteWebSessionStore terminal =
                new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
        terminal.initialize();

        assertEquals(Optional.of("alice"), terminal.usernameFor(token));
    }

    @Test
    void administratorsSeeTheIssuedTokenRequiredByTheAssignment() {
        String token = store.create("alice");
        clock.advance(Duration.ofMinutes(5));

        List<WebSessionInfo> sessions = store.activeSessions();

        assertEquals(1, sessions.size());
        WebSessionInfo info = sessions.get(0);
        assertEquals("alice", info.username());
        assertEquals(token, info.id());
        assertTrue(info.lastSeen().equals(info.createdAt()));
    }

    @Test
    void listingSessionsDoesNotKeepThemAlive() {
        store.create("alice");
        clock.advance(Duration.ofMinutes(20));

        store.activeSessions();
        clock.advance(Duration.ofMinutes(11));

        assertTrue(store.activeSessions().isEmpty());
    }

    @Test
    void revokingByIssuedTokenEndsThatSessionOnly() {
        String alice = store.create("alice");
        String bob = store.create("bob");
        assertTrue(store.revokeSession(alice));

        assertEquals(Optional.empty(), store.usernameFor(alice));
        assertEquals(Optional.of("bob"), store.usernameFor(bob));
    }

    @Test
    void revokingAnUnknownOrMalformedIdChangesNothing() {
        String token = store.create("alice");

        assertFalse(store.revokeSession("zzzzzzzz"));
        assertFalse(store.revokeSession(null));
        assertFalse(store.revokeSession(""));
        assertFalse(store.revokeSession(token.substring(0, 8)));
        assertFalse(store.revokeSession("z".repeat(43)));
        assertFalse(store.revokeSession("%"));

        assertEquals(Optional.of("alice"), store.usernameFor(token));
    }

    @Test
    void administratorCanSeeAndRevokeTheIssuedTokenFromTheTerminal() throws Exception {
        String token = store.create("alice");
        AuditLogRepository audit = mock(AuditLogRepository.class);
        SessionMenu menu = new SessionMenu(new SessionAdmin(store, audit));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        menu.run(new Scanner(token + "\n"), new PrintStream(output, true, StandardCharsets.UTF_8),
                new Session("admin", Role.ADMIN));

        String screen = output.toString(StandardCharsets.UTF_8);
        assertTrue(screen.contains(token), "The admin list must include the issued token");
        assertTrue(screen.contains("alice"));
        assertTrue(screen.contains("last active " + clock.instant()));
        assertTrue(screen.contains("Session revoked."));
        assertEquals(Optional.empty(), store.usernameFor(token));
        verify(audit).record(AuditEventType.DELETE, AuditContentType.SESSION, 0, "admin");
    }

    @Test
    void theDefaultStoreUsesARealClockAndThirtyMinutes() {
        SqliteWebSessionStore real = new SqliteWebSessionStore(temporary.resolve("other.db"));
        real.initialize();

        String token = real.create("alice");

        assertEquals(Optional.of("alice"), real.usernameFor(token));
        assertEquals(Duration.ofMinutes(30), SqliteWebSessionStore.TIMEOUT);
    }
}