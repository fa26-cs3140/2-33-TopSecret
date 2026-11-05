import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageMenuTest {

    private final MessageService service = mock(MessageService.class);
    private final MessageMenu menu = new MessageMenu(service);
    private final Session session = new Session("admin", Role.ADMIN);
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(captured);

    private Map<Integer, String> agents() {
        Map<Integer, String> agents = new LinkedHashMap<>();
        agents.put(1, "Vanguard");
        agents.put(2, "Ghost");
        return agents;
    }

    private void send(String typed) {
        menu.send(new Scanner(typed), out, session);
    }

    private String output() {
        return captured.toString();
    }

    @Test
    void theServiceIsRequired() {
        assertThrows(NullPointerException.class, () -> new MessageMenu(null));
    }

    @Test
    void sendingListsTheAgentsThenSendsAndConfirms() throws Exception {
        when(service.recipients(session)).thenReturn(agents());

        send("2\nMeet at dawn\n");

        verify(service).send(2, "Meet at dawn", session);
        assertTrue(output().contains("1. Vanguard"));
        assertTrue(output().contains("2. Ghost"));
        assertTrue(output().contains("Message sent to Ghost."));
    }

    @Test
    void aNonNumericAgentIdSendsNothing() throws Exception {
        when(service.recipients(session)).thenReturn(agents());

        send("abc\n");

        assertTrue(output().contains("Enter a numeric agent ID."));
        verify(service, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void noAgentsMeansNothingToChoose() throws Exception {
        when(service.recipients(session)).thenReturn(new LinkedHashMap<>());

        send("");

        assertTrue(output().contains("No agents available."));
    }

    @Test
    void endingInputAtTheAgentPromptCancels() throws Exception {
        when(service.recipients(session)).thenReturn(agents());

        send("");

        assertTrue(output().contains("Message cancelled."));
        verify(service, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void endingInputAtTheMessagePromptCancels() throws Exception {
        when(service.recipients(session)).thenReturn(agents());

        send("2\n");

        assertTrue(output().contains("Message cancelled."));
        verify(service, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void aRefusedSendShowsTheReason() throws Exception {
        when(service.recipients(session)).thenReturn(agents());
        doThrow(new ProgramControlException("No agent with ID 9.")).when(service).send(9, "hi", session);

        send("9\nhi\n");

        assertTrue(output().contains("Error: No agent with ID 9."));
    }

    @Test
    void aFailureListingAgentsShowsTheReason() throws Exception {
        when(service.recipients(session)).thenThrow(new ProgramControlException("Agents could not be listed."));

        send("");

        assertTrue(output().contains("Error: Agents could not be listed."));
    }

    @Test
    void anEmptyInboxSaysSo() throws Exception {
        when(service.inbox(session)).thenReturn(List.of());

        menu.showInbox(out, session);

        assertTrue(output().contains("No messages."));
    }

    @Test
    void theInboxShowsWhenWhoAndWhat() throws Exception {
        when(service.inbox(session)).thenReturn(List.of(
                new Message(1, 2, "Ghost", "admin", "Meet at dawn",
                        LocalDateTime.of(2026, 11, 4, 12, 30, 0))));

        menu.showInbox(out, session);

        assertTrue(output().contains("2026-11-04 12:30"));
        assertTrue(output().contains("from admin"));
        assertTrue(output().contains("Meet at dawn"));
    }

    @Test
    void aFailureReadingTheInboxShowsTheReason() throws Exception {
        when(service.inbox(session)).thenThrow(new ProgramControlException("Messages could not be read."));

        menu.showInbox(out, session);

        assertTrue(output().contains("Error: Messages could not be read."));
    }
}