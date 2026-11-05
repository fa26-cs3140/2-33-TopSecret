package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    void standardUserCanListAndReadMissionsFromTheMainMenu() throws Exception {
        Session reader = new Session("reader", Role.STANDARD);
        String missionsOption = missionsOption(reader);
        when(service.list(ContentType.MISSION, reader)).thenReturn(List.of(
                new ContentEntry(7, List.of("Operation Nightfall", "Read the signal.", "2026-10-31"))));
        when(service.read(ContentType.MISSION, 7, reader)).thenReturn(
                new ContentEntry(7, List.of("Operation Nightfall", "Read the signal.", "2026-10-31")));

        String listOutput = run(missionsOption + "\n1\n4\n", reader);
        String detailOutput = run(missionsOption + "\n2\n7\n4\n", reader);

        assertTrue(listOutput.contains("7. Operation Nightfall"));
        assertTrue(listOutput.contains("2026-10-31"));
        assertTrue(detailOutput.contains("title: Operation Nightfall"));
        assertTrue(detailOutput.contains("brief: Read the signal."));
        assertFalse(listOutput.contains("Add entry"));
        assertFalse(listOutput.contains("Update entry"));
        verify(service).list(ContentType.MISSION, reader);
        verify(service).read(ContentType.MISSION, 7, reader);
    }

    @Test
    void adminCanUpdateMissionFieldsThroughTheTerminal() throws Exception {
        String missionsOption = missionsOption(admin);
        when(service.list(ContentType.MISSION, admin)).thenReturn(List.of(
                new ContentEntry(7, List.of("New title", "New brief", "2026-11-04"))));

        String output = run(missionsOption + "\n5\n7\nNew title\nNew brief\n2026-11-04\n4\n", admin);

        verify(service).update(ContentType.MISSION, 7,
                List.of("New title", "New brief", "2026-11-04"), admin);
        assertTrue(output.contains("Entry updated."));
        assertTrue(output.contains("7. New title"));
    }

    @Test
    void readingMissionShowsFacilityAgentsAndAttachedBriefs() throws Exception {
        Session reader = new Session("reader", Role.STANDARD);
        when(service.read(ContentType.MISSION, 7, reader)).thenReturn(
                new ContentEntry(7, List.of("Nightfall", "Mission text", "2026-11-05")));
        when(service.facilityFor(7, reader)).thenReturn(Optional.of(
                new ContentEntry(2, List.of("North Office", "NO"))));
        when(service.agentsFor(7, reader)).thenReturn(List.of(
                new ContentEntry(4, List.of("Ghost", "1992-11-22", "", "", "2"))));
        when(service.briefsFor(7, reader)).thenReturn(List.of(
                new ContentEntry(9, List.of("Signal", "2026-11-04", "Details"))));

        String output = run("11\n2\n7\n4\n4\n", reader);

        assertTrue(output.contains("Facility: 2. North Office"));
        assertTrue(output.contains("Agents: 4. Ghost"));
        assertTrue(output.contains("Briefs: 9. Signal"));
        assertFalse(output.contains("Set mission facility"));
        verify(service).facilityFor(7, reader);
        verify(service).agentsFor(7, reader);
        verify(service).briefsFor(7, reader);
    }

    @Test
    void adminCanManageExistingMissionRelationships() throws Exception {
        String output = run("11\n6\n7\n2\n7\n7\n4\n8\n7\n9\n4\n4\n", admin);

        verify(service).setFacility(7, 2, admin);
        verify(service).assignAgent(7, 4, admin);
        verify(service).attachBrief(7, 9, admin);
        assertTrue(output.contains("6) Set mission facility"));
        assertTrue(output.contains("7) Assign agent to mission"));
        assertTrue(output.contains("8) Attach brief to mission"));
        assertTrue(output.contains("Facility saved."));
        assertTrue(output.contains("Agent assigned."));
        assertTrue(output.contains("Brief attached."));
    }

    @Test
    void standardUserCannotChangeMissionRelationships() throws Exception {
        Session reader = new Session("reader", Role.STANDARD);
        String output = run("11\n6\n7\n8\n4\n4\n", reader);

        assertFalse(output.contains("Set mission facility"));
        assertFalse(output.contains("Assign agent to mission"));
        assertFalse(output.contains("Attach brief to mission"));
        assertTrue(output.contains("Access denied."));
        verify(service, never()).setFacility(anyInt(), anyInt(), any());
        verify(service, never()).assignAgent(anyInt(), anyInt(), any());
        verify(service, never()).attachBrief(anyInt(), anyInt(), any());
    }

    @Test
    void agentUpdateCanOverwriteOptionalFieldsWithBlankText() throws Exception {
        when(service.list(ContentType.FACILITY, admin)).thenReturn(List.of(
                new ContentEntry(2, List.of("Field Office", "FO"))));

        String output = run("2\n5\n7\nGhost\n1992-11-22\n\n\n2\n4\n", admin);

        verify(service).update(ContentType.AGENT, 7,
                List.of("Ghost", "1992-11-22", "", "", "2"), admin);
        assertTrue(output.contains("Choose an existing facility ID:"));
        assertTrue(output.contains("2. Field Office"));
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

    @Test
    void userInterfaceWithAMessageServiceOffersTheMessageOptions() {
        ProgramControl control = mock(ProgramControl.class);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new UserInterface(control, Effects.NONE, service, mock(AuthenticationService.class),
                mock(MessageService.class))
                .runMenu(new Scanner("4\n"), new PrintStream(buffer), admin);
        assertTrue(buffer.toString().contains("9) Send a message"));
        verifyNoInteractions(control);
    }

    @Test
    void standardUserCannotSeeOrRunUpdate() throws Exception {
        Session standard = new Session("reader", Role.STANDARD);
        String output = run("1\n5\n4\n4\n", standard);
        assertFalse(output.contains("Update entry"));
        assertTrue(output.contains("Access denied."));
        verifyNoInteractions(service);
    }

    @Test
    void updateWithNonNumericIdReportsErrorAfterFieldsAreEntered() throws Exception {
        String output = run("1\n5\nabc\nT\n2026-01-01\nB\n4\n4\n", admin);
        assertTrue(output.contains("Enter a numeric entry ID."));
        verify(service, never()).update(any(), anyInt(), any(), any());
    }

    @Test
    void updateServiceErrorIsReportedAndMenuContinues() throws Exception {
        doThrow(new ProgramControlException("No entry with ID 9."))
                .when(service).update(eq(ContentType.BRIEF), eq(9), anyList(), eq(admin));
        String output = run("1\n5\n9\nT\n2026-01-01\nB\n4\n4\n", admin);
        assertTrue(output.contains("Error: No entry with ID 9."));
    }

    @Test
    void updateCancelsOnEndOfInput() throws Exception {
        assertTrue(run("1\n5\n9\nNew\n", admin).contains("Entry cancelled."));
        run("1\n5\n", admin);   // EOF at the ID prompt
        verify(service, never()).update(any(), anyInt(), any(), any());
    }

    private String missionsOption(Session session) {
        Matcher matcher = Pattern.compile("(?m)^(\\d+)\\) Missions$").matcher(run("", session));
        assertTrue(matcher.find(), "The main menu should offer Missions");
        assertEquals("11", matcher.group(1), "Options 9 and 10 are reserved for messages");
        return matcher.group(1);
    }

    /* messaging */

    private String runWithMessages(String input, Session session, MessageService messages) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ContentMenu(service, null, messages).run(new Scanner(input), new PrintStream(buffer), session);
        return buffer.toString();
    }

    @Test
    void messageOptionsAreOfferedToEveryoneSignedInWhenMessagingIsWired() {
        MessageService messages = mock(MessageService.class);
        Session standard = new Session("reader", Role.STANDARD);

        String output = runWithMessages("4\n", standard, messages);

        assertTrue(output.contains("9) Send a message"));
        assertTrue(output.contains("10) My messages"));
    }

    @Test
    void messageOptionsAreHiddenAndRefusedWhenMessagingIsNotWired() {
        String output = runWithMessages("9\n10\n4\n", admin, null);

        assertFalse(output.contains("Send a message"));
        assertTrue(output.contains("Please choose a displayed option."));
    }

    @Test
    void choosingSendDelegatesToTheMessageScreen() throws Exception {
        MessageService messages = mock(MessageService.class);
        when(messages.recipients(admin)).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(2, "Ghost")));

        String output = runWithMessages("9\n2\nMeet at dawn\n4\n", admin, messages);

        verify(messages).send(2, "Meet at dawn", admin);
        assertTrue(output.contains("Message sent to Ghost."));
    }

    @Test
    void choosingMyMessagesShowsTheInbox() throws Exception {
        MessageService messages = mock(MessageService.class);
        Session ghost = new Session("Ghost", Role.STANDARD);
        when(messages.inbox(ghost)).thenReturn(List.of(new Message(1, 2, "Ghost", "admin",
                "Meet at dawn", LocalDateTime.of(2026, 11, 4, 12, 0))));

        String output = runWithMessages("10\n4\n", ghost, messages);

        assertTrue(output.contains("Meet at dawn"));
    }

    /* creating a mission, which needs a facility and a first agent */

    @Test
    void creatingAMissionPromptsForItsFacilityAndItsFirstAgent() throws Exception {
        String missions = missionsOption(admin);
        when(service.list(ContentType.FACILITY, admin)).thenReturn(List.of(
                new ContentEntry(1, List.of("Langley Annex", "LGA")),
                new ContentEntry(2, List.of("Berlin Station", "BER"))));
        when(service.list(ContentType.AGENT, admin)).thenReturn(List.of(
                new ContentEntry(4, List.of("Nightjar", "1940-06-01", "", "Berlin.", "2"))));
        when(service.addMission(anyList(), anyInt(), anyInt(), eq(admin))).thenReturn(7);
        when(service.facilityFor(7, admin)).thenReturn(
                Optional.of(new ContentEntry(2, List.of("Berlin Station", "BER"))));
        when(service.agentsFor(7, admin)).thenReturn(List.of(
                new ContentEntry(4, List.of("Nightjar", "1940-06-01", "", "Berlin.", "2"))));
        when(service.briefsFor(7, admin)).thenReturn(List.of());

        String output = run(missions
                + "\n3\nOperation Nightfall\nWatch the north gate.\n2026-11-05\n2\n4\n4\n4\n", admin);

        verify(service).addMission(
                List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05"), 2, 4, admin);
        assertTrue(output.contains("Mission 7 added."), output);
        assertTrue(output.contains("Berlin Station"), output);
    }

    @Test
    void theFacilityAndAgentListsAreShownBeforeTheirPrompts() throws Exception {
        String missions = missionsOption(admin);
        when(service.list(ContentType.FACILITY, admin)).thenReturn(List.of(
                new ContentEntry(1, List.of("Langley Annex", "LGA"))));
        when(service.list(ContentType.AGENT, admin)).thenReturn(List.of(
                new ContentEntry(3, List.of("Cardinal", "1931-02-14", "", "Langley.", "1"))));
        when(service.addMission(anyList(), anyInt(), anyInt(), eq(admin))).thenReturn(1);
        when(service.facilityFor(1, admin)).thenReturn(Optional.empty());
        when(service.agentsFor(1, admin)).thenReturn(List.of());
        when(service.briefsFor(1, admin)).thenReturn(List.of());

        String output = run(missions + "\n3\nTitle\nBrief.\n2026-11-05\n1\n3\n4\n4\n", admin);

        assertTrue(output.contains("1. Langley Annex"), output);
        assertTrue(output.contains("3. Cardinal"), output);
    }

    @Test
    void theMissionsMenuOffersAddEntryToAnAdmin() {
        String output = run(missionsOption(admin) + "\n4\n4\n", admin);

        assertTrue(output.contains("3) Add entry"), output);
        assertFalse(output.contains("Mission creation is unavailable"), output);
    }

    @Test
    void aStandardUserIsNotOfferedMissionCreation() {
        Session reader = new Session("reader", Role.STANDARD);

        String output = run(missionsOption(reader) + "\n4\n4\n", reader);

        assertFalse(output.contains("3) Add entry"), output);
    }

    @Test
    void anEndOfInputAtTheAgentPromptCreatesNothing() throws Exception {
        String missions = missionsOption(admin);
        when(service.list(ContentType.FACILITY, admin)).thenReturn(List.of(
                new ContentEntry(1, List.of("Langley Annex", "LGA"))));
        when(service.list(ContentType.AGENT, admin)).thenReturn(List.of());

        run(missions + "\n3\nTitle\nBrief.\n2026-11-05\n1\n", admin);

        verify(service, never()).addMission(any(), anyInt(), anyInt(), any());
    }

    @Test
    void aRefusedMissionIsReportedAndTheMenuCarriesOn() throws Exception {
        String missions = missionsOption(admin);
        when(service.list(ContentType.FACILITY, admin)).thenReturn(List.of(
                new ContentEntry(1, List.of("Langley Annex", "LGA"))));
        when(service.list(ContentType.AGENT, admin)).thenReturn(List.of());
        when(service.addMission(anyList(), anyInt(), anyInt(), eq(admin)))
                .thenThrow(new ProgramControlException("That agent does not exist."));

        String output = run(missions + "\n3\nTitle\nBrief.\n2026-11-05\n1\n404\n4\n4\n", admin);

        assertTrue(output.contains("Error: That agent does not exist."), output);
        assertTrue(output.contains("1) List entries"), output);
    }
}
