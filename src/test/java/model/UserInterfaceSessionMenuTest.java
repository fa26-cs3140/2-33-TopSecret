package model;

import controller.ProgramControl;
import view.Effects;
import view.UserInterface;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserInterfaceSessionMenuTest {

    @Test
    void theSessionAdminReachesTheMenuThroughTheUserInterface() {
        SessionAdministration store = mock(SessionAdministration.class);
        when(store.activeSessions()).thenReturn(List.of(new WebSessionInfo("abcd1234", "alice",
                Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T10:12:00Z"))));
        UserInterface ui = new UserInterface(mock(ProgramControl.class), Effects.NONE,
                mock(ContentService.class), null, null,
                new SessionAdmin(store, mock(AuditLogRepository.class)));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        ui.runMenu(new Scanner("14\n\n4\n"), new PrintStream(buffer), new Session("admin", Role.ADMIN));

        assertTrue(buffer.toString().contains("Active web sessions"), buffer.toString());
    }
}