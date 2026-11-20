package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Relationships read from either end, and one search across every text field. */
class SqliteContentServiceRelatedTest {

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /**
     * Country 1 France; facility 1 Langley Annex (in France) and 2 Berlin
     * Station; agent 1 Cardinal at facility 1 and 2 Nightjar at facility 2;
     * mission 1 Operation Nightfall run by facility 1 with agent 1; brief 1
     * attached to that mission, brief 2 attached to nothing.
     */
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
        service.add(ContentType.BRIEF,
                List.of("Loose brief", "2026-11-07", "Attached to nothing."), admin);
        service.attachBrief(1, 1, admin);
        service.setCountry(1, 1, admin);
        return service;
    }

    private static List<String> names(List<ContentEntry> entries) {
        return entries.stream().map(entry -> entry.values().get(0)).toList();
    }

    private static Set<ContentType> types(List<ContentHit> hits) {
        return hits.stream().map(ContentHit::type).collect(Collectors.toSet());
    }

    @Test
    void aFacilityListsItsAgentsAndItsMissions() throws Exception {
        SqliteContentService service = service();

        assertEquals(List.of("Cardinal"), names(service.agentsAt(1, reader)));
        assertEquals(List.of("Nightjar"), names(service.agentsAt(2, reader)));
        assertEquals(List.of("Operation Nightfall"), names(service.missionsAt(1, reader)));
        assertTrue(service.missionsAt(2, reader).isEmpty());
        assertTrue(service.agentsAt(99, reader).isEmpty());
    }

    @Test
    void anAgentShowsItsFacilityAndItsMissions() throws Exception {
        SqliteContentService service = service();

        assertEquals("Berlin Station", service.facilityOf(2, reader).orElseThrow().values().get(0));
        assertTrue(service.facilityOf(99, reader).isEmpty());
        assertEquals(List.of("Operation Nightfall"), names(service.missionsFor(1, reader)));
        assertTrue(service.missionsFor(2, reader).isEmpty());
    }

    @Test
    void aCountryListsItsFacilitiesAndABriefShowsItsMission() throws Exception {
        SqliteContentService service = service();

        assertEquals(List.of("Langley Annex"), names(service.facilitiesIn(1, reader)));
        assertTrue(service.facilitiesIn(99, reader).isEmpty());
        assertEquals("Operation Nightfall", service.missionOf(1, reader).orElseThrow().values().get(0));
        assertTrue(service.missionOf(2, reader).isEmpty());
    }

    @Test
    void readingRelatedRecordsIsAuditedPerRecordAndPerReader() throws Exception {
        SqliteContentService service = service();

        service.agentsAt(1, reader);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getUsername().equals("reader")
                        && entry.getEventType() == AuditEventType.READ
                        && entry.getContentType() == AuditContentType.AGENT
                        && entry.getContentId() == 1));
    }

    @Test
    void relatedRecordsRequireASession() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.agentsAt(1, null));
        assertThrows(ProgramControlException.class, () -> service.missionOf(1, null));
    }

    @Test
    void searchFindsMatchesInEveryTextFieldOfEveryType() throws Exception {
        SqliteContentService service = service();

        assertEquals(Set.of(ContentType.MISSION, ContentType.BRIEF),
                types(service.search("north gate", reader)));
        assertEquals(Set.of(ContentType.AGENT, ContentType.FACILITY),
                types(service.search("langley", reader)));
        assertEquals(Set.of(ContentType.COUNTRY), types(service.search("france", reader)));
        assertEquals(Set.of(ContentType.AGENT), types(service.search("1931", reader)));
    }

    @Test
    void searchIgnoresCaseAndSurroundingSpaces() throws Exception {
        SqliteContentService service = service();

        List<ContentHit> hits = service.search("  CARDINAL ", reader);

        assertEquals(1, hits.size());
        assertEquals(ContentType.AGENT, hits.get(0).type());
        assertEquals(1, hits.get(0).entry().id());
    }

    @Test
    void searchTreatsWildcardCharactersAsOrdinaryText() throws Exception {
        SqliteContentService service = service();

        assertTrue(service.search("%", reader).isEmpty());
        assertTrue(service.search("_", reader).isEmpty());
        assertTrue(service.search("\\", reader).isEmpty());
    }

    @Test
    void searchDoesNotMatchIdentifierColumns() throws Exception {
        SqliteContentService service = service();

        // agent 2 has facility_id 2, but no text field contains the word "facility"
        assertTrue(service.search("facility_id", reader).isEmpty());
    }

    @Test
    void searchRejectsABlankPhraseAndAMissingSession() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.search("   ", reader));
        assertThrows(ProgramControlException.class, () -> service.search(null, reader));
        assertThrows(ProgramControlException.class, () -> service.search("gate", null));
    }

    @Test
    void everyHitIsAuditedAsARead() throws Exception {
        SqliteContentService service = service();

        service.search("north gate", reader);

        List<AuditLogEntry> reads = service.audit(admin).stream()
                .filter(entry -> entry.getUsername().equals("reader")).toList();
        assertTrue(reads.stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION && entry.getContentId() == 1));
        assertTrue(reads.stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.BRIEF && entry.getContentId() == 1));
    }
}