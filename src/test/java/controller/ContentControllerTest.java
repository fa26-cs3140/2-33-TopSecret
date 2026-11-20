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

    @Test
    void relatedRecordsAndSearchPassThroughToTheModel() throws Exception {
        ContentService service = mock(ContentService.class);
        ContentController controller = new ContentController(service, mock(AuthenticationService.class));
        Session reader = new Session("reader", Role.STANDARD);
        ContentEntry entry = new ContentEntry(4, List.of("Ghost"));
        ContentHit hit = new ContentHit(ContentType.AGENT, entry);
        when(service.agentsAt(2, reader)).thenReturn(List.of(entry));
        when(service.missionsAt(2, reader)).thenReturn(List.of(entry));
        when(service.missionsFor(4, reader)).thenReturn(List.of(entry));
        when(service.facilityOf(4, reader)).thenReturn(Optional.of(entry));
        when(service.facilitiesIn(7, reader)).thenReturn(List.of(entry));
        when(service.missionOf(3, reader)).thenReturn(Optional.of(entry));
        when(service.search("ghost", reader)).thenReturn(List.of(hit));

        assertEquals(List.of(entry), controller.agentsAt(2, reader));
        assertEquals(List.of(entry), controller.missionsAt(2, reader));
        assertEquals(List.of(entry), controller.missionsFor(4, reader));
        assertEquals(Optional.of(entry), controller.facilityOf(4, reader));
        assertEquals(List.of(entry), controller.facilitiesIn(7, reader));
        assertEquals(Optional.of(entry), controller.missionOf(3, reader));
        assertEquals(List.of(hit), controller.search("ghost", reader));
    }
}
