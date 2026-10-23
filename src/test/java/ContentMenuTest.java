import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentMenuTest {
    private final ContentService service = mock(ContentService.class);
    private final Session admin = new Session("admin", Role.ADMIN);

    private String run(String input, Session session) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ContentMenu(service).run(new Scanner(input), new PrintStream(buffer), session);
        return buffer.toString();
    }

    @Test
    void promptsForEveryBriefFieldAndImmediatelyListsSavedEntry() throws Exception {
        when(service.list(ContentType.BRIEF, admin)).thenReturn(List.of(new ContentEntry(9, List.of("Report", "2026-10-22", "Text"))));
        String output = run("1\n3\nReport\n2026-10-22\nText\n4\n4\n", admin);
        verify(service).add(ContentType.BRIEF, List.of("Report", "2026-10-22", "Text"), admin);
        assertTrue(output.contains("9. Report"));
        assertTrue(output.contains("Entry added."));
    }

    @Test
    void adminCanUpdateAnEntryAndImmediatelySeeTheResult() throws Exception {
        when(service.list(ContentType.FACILITY, admin)).thenReturn(
                List.of(new ContentEntry(9, List.of("Field Office", "FO"))));

        String output = run("3\n5\n9\nField Office\nFO\n4\n4\n", admin);

        verify(service).update(ContentType.FACILITY, 9,
                List.of("Field Office", "FO"), admin);
        assertTrue(output.contains("Entry updated."));
        assertTrue(output.contains("9. Field Office"));
    }

    @Test
    void standardUserCannotInvokeHiddenActions() throws Exception {
        Session standard = new Session("reader", Role.STANDARD);
        String output = run("5\n1\n3\n5\n4\n4\n", standard);
        assertFalse(output.contains("Review audit log"));
        assertFalse(output.contains("Add entry"));
        assertFalse(output.contains("Update entry"));
        assertTrue(output.contains("Access denied."));
        verifyNoInteractions(service);
    }

    @Test
    void auditShowsActorTimeOperationAndContent() throws Exception {
        when(service.audit(admin)).thenReturn(List.of(new AuditLogEntry(AuditEventType.READ,
                AuditContentType.AGENT, 7, "reader", LocalDateTime.of(2026, 10, 22, 12, 30))));
        String output = run("5\n4\n", admin);
        assertTrue(output.contains("2026-10-22T12:30 | reader | READ | AGENT #7"));
    }

    @Test
    void errorsAndInvalidInputDoNotEndTheMenu() throws Exception {
        when(service.list(ContentType.FACILITY, admin)).thenThrow(new ProgramControlException("Unavailable"));
        when(service.read(ContentType.FACILITY, 1, admin)).thenReturn(new ContentEntry(1, List.of("HQ", "HQ")));
        String output = run("oops\n3\nwrong\n1\n2\nabc\n2\n1\n4\n4\n", admin);
        assertTrue(output.contains("Error: Unavailable"));
        assertTrue(output.contains("numeric entry ID"));
        assertTrue(output.contains("abbreviation: HQ"));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void eofCancelsEachPromptWithoutSaving() throws Exception {
        for (String input : List.of("", "1\n", "1\n2\n", "1\n3\nTitle\n", "2\n3\n",
                "1\n5\n", "1\n5\n1\nTitle\n")) {
            run(input, admin);
        }
        verify(service, never()).add(any(), anyList(), any());
        verify(service, never()).update(any(), anyInt(), anyList(), any());
        assertTrue(run("", null).contains("Please log in"));
    }

    @Test
    void emptyListsAndAuditAreExplained() throws Exception {
        when(service.list(ContentType.AGENT, admin)).thenReturn(List.of());
        when(service.audit(admin)).thenReturn(List.of());
        String output = run("2\n1\n4\n5\n4\n", admin);
        assertTrue(output.contains("No entries available."));
        assertTrue(output.contains("Audit log is empty."));
    }

    @Test
    void accountMenusDelegateToAuthenticationAndHandleErrorsAndEof() throws Exception {
        AuthenticationService authentication = mock(AuthenticationService.class);
        when(authentication.listUsernames()).thenReturn(List.of("admin", "newuser"));
        doThrow(new CredentialException("Cannot demote yourself")).when(authentication).demote("admin", "admin");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ContentMenu(service, authentication).run(new Scanner("6\nnewuser\n7\nnewuser\n8\nnewuser\n8\nadmin\n6\n"), new PrintStream(buffer), admin);
        verify(authentication).createUser("newuser");
        verify(authentication).promote("admin", "newuser");
        verify(authentication).demote("admin", "newuser");
        assertTrue(buffer.toString().contains("Cannot demote yourself"));
        assertTrue(run("6\n4\n", admin).contains("Access denied."));
    }

    @Test
    void newUserInterfaceConstructorsRouteAuthenticatedSessionsToContentMenu() {
        ProgramControl control = mock(ProgramControl.class);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new UserInterface(control, Effects.NONE, service).runMenu(new Scanner("4\n"), new PrintStream(buffer), admin);
        new UserInterface(control, Effects.NONE, service, mock(AuthenticationService.class))
                .runMenu(new Scanner("4\n"), new PrintStream(buffer), admin);
        assertTrue(buffer.toString().contains("1) Briefs"));
        verifyNoInteractions(control);
    }
}
