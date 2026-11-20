package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionAdminTest {

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session standard = new Session("pat", Role.STANDARD);
    private final SessionAdministration store = mock(SessionAdministration.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final SessionAdmin sessions = new SessionAdmin(store, audit);
    private final WebSessionInfo info = new WebSessionInfo("abcd1234", "alice",
            Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T10:12:00Z"));

    @Test
    void anAdministratorSeesTheActiveSessions() throws Exception {
        when(store.activeSessions()).thenReturn(List.of(info));

        assertEquals(List.of(info), sessions.list(admin));
    }

    @Test
    void everyoneElseIsRefusedAndNothingIsTouched() {
        assertThrows(ProgramControlException.class, () -> sessions.list(standard));
        assertThrows(ProgramControlException.class, () -> sessions.list(null));
        assertThrows(ProgramControlException.class, () -> sessions.revoke("abcd1234", standard));

        verifyNoInteractions(store, audit);
    }

    @Test
    void revokingASessionEndsItAndIsAudited() throws Exception {
        when(store.revokeSession("abcd1234")).thenReturn(true);

        assertTrue(sessions.revoke("abcd1234", admin));

        verify(audit).record(AuditEventType.DELETE, AuditContentType.SESSION, 0, "admin");
    }

    @Test
    void revokingAnUnknownSessionAuditsNothing() throws Exception {
        when(store.revokeSession("nope")).thenReturn(false);

        assertFalse(sessions.revoke("nope", admin));

        verifyNoInteractions(audit);
    }

    @Test
    void anAuditFailureIsReportedAsAProgramControlProblem() throws Exception {
        when(store.revokeSession("abcd1234")).thenReturn(true);
        doThrow(new AuditLogException("disk full")).when(audit)
                .record(AuditEventType.DELETE, AuditContentType.SESSION, 0, "admin");

        assertThrows(ProgramControlException.class, () -> sessions.revoke("abcd1234", admin));
    }
}