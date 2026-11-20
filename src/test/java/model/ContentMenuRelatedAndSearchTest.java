package model;

import view.ContentMenu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Terminal side of related records (read an entry), search (13) and session admin (14). */
class ContentMenuRelatedAndSearchTest {

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /** Same data as SqliteContentServiceRelatedTest: ids 1 and 2 of each kind, mission 1, brief 1 attached. */
    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("missions.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService service =
                new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        service.add(ContentType.COUNTRY, List.of("France"), admin);
        service.add(ContentType.FACILITY, List.of("Langley Annex", "LGA"), admin);
        service.add(ContentType.FACILITY, List.of("Berlin Station", "BER"), admin);
        service.add(ContentType.AGENT,
                List.of("Cardinal", "1931-02-14", "1979-04-02", "Langley.", "1"), admin);
        service.add(ContentType.AGENT,
                List.of("Nightjar", "1940-06-01", "1988-12-19", "Berlin.", "2"), admin);
        service.addMission(
                List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05"), 1, 1, admin);
        service.add(ContentType.BRIEF,
                List.of("Gate report", "2026-11-06", "The north gate was unguarded."), admin);
        service.attachBrief(1, 1, admin);
        service.setCountry(1, 1, admin);
        return service;
    }

    private String run(ContentMenu menu, Session session, String input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        menu.run(new Scanner(input), new PrintStream(buffer), session);
        return buffer.toString();
    }

    @Test
    void readingAnAgentShowsItsFacilityAndMissions() throws Exception {
        String output = run(new ContentMenu(service()), reader, "2\n2\n1\n4\n4\n");

        assertTrue(output.contains("Facility: 1. Langley Annex"), output);
        assertTrue(output.contains("Missions: 1. Operation Nightfall (2026-11-05)"), output);
    }

    @Test
    void readingAFacilityShowsItsAgentsAndMissions() throws Exception {
        String output = run(new ContentMenu(service()), reader, "3\n2\n1\n4\n4\n");

        assertTrue(output.contains("Agents: 1. Cardinal"), output);
        assertTrue(output.contains("Missions: 1. Operation Nightfall (2026-11-05)"), output);
    }

    @Test
    void aFacilityWithNoMissionsSaysNone() throws Exception {
        String output = run(new ContentMenu(service()), reader, "3\n2\n2\n4\n4\n");

        assertTrue(output.contains("Missions: None"), output);
    }

    @Test
    void readingACountryShowsItsFacilities() throws Exception {
        String output = run(new ContentMenu(service()), reader, "12\n2\n1\n4\n4\n");

        assertTrue(output.contains("Facilities: 1. Langley Annex"), output);
    }

    @Test
    void readingABriefShowsItsMission() throws Exception {
        String output = run(new ContentMenu(service()), reader, "1\n2\n1\n4\n4\n");

        assertTrue(output.contains("Mission: 1. Operation Nightfall (2026-11-05)"), output);
    }

    @Test
    void searchIsOfferedToEveryoneAndListsHitsByKind() throws Exception {
        String output = run(new ContentMenu(service()), reader, "13\nnorth gate\n4\n");

        assertTrue(output.contains("13) Search"), output);
        assertTrue(output.contains("missions: 1. Operation Nightfall (2026-11-05)"), output);
        assertTrue(output.contains("briefs: 1. Gate report"), output);
    }

    @Test
    void searchSaysWhenNothingMatches() throws Exception {
        assertTrue(run(new ContentMenu(service()), reader, "13\nzzzz\n4\n").contains("No matches."));
    }

    @Test
    void aBlankSearchIsReportedNotCrashed() throws Exception {
        String output = run(new ContentMenu(service()), reader, "13\n\n4\n");

        assertTrue(output.contains("Error: Search phrase cannot be empty."), output);
    }

    @Test
    void sessionAdministrationIsOfferedToAdministratorsOnly() throws Exception {
        SessionAdministration store = mock(SessionAdministration.class);
        when(store.activeSessions()).thenReturn(List.of(new WebSessionInfo("abcd1234", "alice",
                Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T10:12:00Z"))));
        SessionAdmin sessions = new SessionAdmin(store, mock(AuditLogRepository.class));
        SqliteContentService service = service();

        String asAdmin = run(new ContentMenu(service, null, null, sessions), admin, "14\n\n4\n");
        String asReader = run(new ContentMenu(service, null, null, sessions), reader, "14\n4\n");

        assertTrue(asAdmin.contains("14) Manage web sessions"), asAdmin);
        assertTrue(asAdmin.contains("abcd1234"), asAdmin);
        assertFalse(asReader.contains("14) Manage web sessions"), asReader);
        assertTrue(asReader.contains("Access denied."), asReader);
    }

    @Test
    void withoutASessionAdminOptionFourteenIsNotOffered() throws Exception {
        String output = run(new ContentMenu(service()), admin, "14\n4\n");

        assertFalse(output.contains("14) Manage web sessions"), output);
        assertTrue(output.contains("Access denied."), output);
    }
}