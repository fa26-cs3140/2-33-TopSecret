package controller;

import model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentControllerTest {
    @Test
    void terminalAndWebShareModelResultsAndAuditAuthorization() throws Exception {
        ContentService service = mock(ContentService.class);
        AuthenticationService auth = mock(AuthenticationService.class);
        ContentController controller = new ContentController(service, auth);
        Session admin = new Session("agent", Role.ADMIN);
        Session standard = new Session("other", Role.STANDARD);
        ContentEntry entry = new ContentEntry(1, List.of("title", "date", "text"));
        when(auth.sessionFor("agent")).thenReturn(admin);
        when(service.list(ContentType.BRIEF, admin)).thenReturn(List.of(entry));
        when(service.read(ContentType.BRIEF, 1, admin)).thenReturn(entry);
        assertSame(admin, controller.sessionFor("agent"));
        assertEquals(List.of(entry), controller.list(ContentType.BRIEF, admin));
        assertSame(entry, controller.read(ContentType.BRIEF, 1, admin));
        assertTrue(controller.canViewAudit(admin));
        assertFalse(controller.canViewAudit(standard));
        assertFalse(controller.canViewAudit(null));
        assertThrows(ProgramControlException.class, () -> controller.audit(standard));
        verify(service, never()).audit(standard);
        assertEquals(List.of(), controller.audit(admin));
        verify(service).audit(admin);
    }

    @Test
    void missionRelationshipsUseTheExistingModelService() throws Exception {
        ContentService service = mock(ContentService.class);
        ContentController controller = new ContentController(service, mock(AuthenticationService.class));
        Session reader = new Session("reader", Role.STANDARD);
        ContentEntry facility = new ContentEntry(2, List.of("North Office", "NO"));
        ContentEntry agent = new ContentEntry(4, List.of("Ghost", "1992-11-22", "", "", "2"));
        ContentEntry brief = new ContentEntry(9, List.of("Signal", "2026-11-04", "Details"));
        when(service.facilityFor(7, reader)).thenReturn(Optional.of(facility));
        when(service.agentsFor(7, reader)).thenReturn(List.of(agent));
        when(service.briefsFor(7, reader)).thenReturn(List.of(brief));

        assertEquals(Optional.of(facility), controller.facilityFor(7, reader));
        assertEquals(List.of(agent), controller.agentsFor(7, reader));
        assertEquals(List.of(brief), controller.briefsFor(7, reader));
    }
}
