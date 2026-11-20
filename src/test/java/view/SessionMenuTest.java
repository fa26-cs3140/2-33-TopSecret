package view;

import model.AuditLogRepository;
import model.Role;
import model.Session;
import model.SessionAdmin;
import model.SessionAdministration;
import model.WebSessionInfo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionMenuTest {

    private final Session admin = new Session("admin", Role.ADMIN);
    private final SessionAdministration store = mock(SessionAdministration.class);
    private final SessionMenu menu = new SessionMenu(new SessionAdmin(store, mock(AuditLogRepository.class)));
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private String run(String input) throws Exception {
        menu.run(new Scanner(input), new PrintStream(buffer), admin);
        return buffer.toString();
    }

    private void oneSession() {
        when(store.activeSessions()).thenReturn(List.of(new WebSessionInfo("abcd1234", "alice",
                Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T10:12:00Z"))));
    }

    @Test
    void listsEachSessionAndStatesTheTimeout() throws Exception {
        oneSession();

        String output = run("\n");

        assertTrue(output.contains("abcd1234"));
        assertTrue(output.contains("alice"));
        assertTrue(output.contains("30 minutes"));
    }

    @Test
    void saysWhenThereAreNoSessions() throws Exception {
        when(store.activeSessions()).thenReturn(List.of());

        assertTrue(run("").contains("No active web sessions."));
    }

    @Test
    void aBlankAnswerGoesBackWithoutRevoking() throws Exception {
        oneSession();

        run("\n");

        verify(store, never()).revokeSession(anyString());
    }

    @Test
    void endOfInputGoesBackQuietly() throws Exception {
        oneSession();

        assertFalse(run("").contains("revoked"));
    }

    @Test
    void revokesTheSessionTheAdministratorNames() throws Exception {
        oneSession();
        when(store.revokeSession("abcd1234")).thenReturn(true);

        assertTrue(run("abcd1234\n").contains("Session revoked."));
    }

    @Test
    void reportsAnIdThatMatchesNothing() throws Exception {
        oneSession();
        when(store.revokeSession("zzzz")).thenReturn(false);

        assertTrue(run("zzzz\n").contains("No session with that ID."));
    }
}