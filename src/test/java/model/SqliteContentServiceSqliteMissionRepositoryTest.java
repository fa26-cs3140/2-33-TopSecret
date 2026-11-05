package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Missions as a content type. Both classes write the missions table, so these
 * check they agree about it and that a mission read reaches the audit log.
 */
class SqliteContentServiceSqliteMissionRepositoryTest {

    private static final String MISSIONS = "Title\tDate\tText\n"
            + "Operation Sandtrap\t1970-11-03\tBug the diplomatic pouch in Rome.\n"
            + "The Warsaw Whisper\t1975-08-12\tIntercept the underground press.\n";

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    private SqliteContentService service(Path database) throws Exception {
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        return new SqliteContentService(database, audit, files);
    }

    private SqliteMissionRepository repository(Path database) {
        FileHandler files = mock(FileHandler.class);
        when(files.readText("mission_briefs.tsv")).thenReturn(MISSIONS);
        return new SqliteMissionRepository(files, database, "mission_briefs.tsv");
    }

    /* the two writers agreeing about one table */

    @Test
    void theContentServiceCreatesTheMissionsTableOnItsOwn() throws Exception {
        Path database = temporary.resolve("alone.db");

        service(database).initialize();

        assertEquals(List.of(), service(database).list(ContentType.MISSION, reader));
    }

    @Test
    void missionsImportedByTheRepositoryAreVisibleToTheContentService() throws Exception {
        Path database = temporary.resolve("shared.db");
        repository(database).initialize();
        SqliteContentService service = service(database);
        service.initialize();

        List<ContentEntry> missions = service.list(ContentType.MISSION, reader);

        assertEquals(2, missions.size());
        assertEquals(List.of("Operation Sandtrap", "Bug the diplomatic pouch in Rome.", "1970-11-03"),
                service.read(ContentType.MISSION, 1, reader).values());
    }

    @Test
    void aMissionAddedThroughTheContentServiceIsVisibleToTheRepository() throws Exception {
        Path database = temporary.resolve("roundtrip.db");
        SqliteContentService service = service(database);
        service.initialize();

        service.add(ContentType.MISSION,
                List.of("Operation Nightfall", "Watch the north gate.", "2026-10-31"), admin);

        List<Mission> missions = repository(database).getAllMissions();
        assertEquals(1, missions.size());
        assertEquals("Operation Nightfall", missions.get(0).getTitle());
        assertEquals("Watch the north gate.", missions.get(0).getBrief());
    }

    /* the audit log must survive a mission event */

    @Test
    void readingAMissionIsAuditedAndTheLogStillOpens() throws Exception {
        Path database = temporary.resolve("audited.db");
        repository(database).initialize();
        SqliteContentService service = service(database);
        service.initialize();

        service.read(ContentType.MISSION, 1, reader);

        List<AuditLogEntry> entries = service.audit(admin);
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION
                        && entry.getEventType() == AuditEventType.READ
                        && entry.getUsername().equals("reader")));
    }

    @Test
    void addingAMissionIsAuditedAsACreate() throws Exception {
        Path database = temporary.resolve("created.db");
        SqliteContentService service = service(database);
        service.initialize();

        service.add(ContentType.MISSION, List.of("Operation Dusk", "Brief.", "2026-11-01"), admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION
                        && entry.getEventType() == AuditEventType.CREATE));
    }

    /* roles apply to missions like every other type */

    @Test
    void aStandardUserMayReadMissionsButNotChangeThem() throws Exception {
        Path database = temporary.resolve("roles.db");
        repository(database).initialize();
        SqliteContentService service = service(database);
        service.initialize();

        assertEquals(2, service.list(ContentType.MISSION, reader).size());
        assertThrows(ProgramControlException.class, () -> service.add(ContentType.MISSION,
                List.of("Nope", "Brief.", "2026-11-01"), reader));
        assertThrows(ProgramControlException.class, () -> service.update(ContentType.MISSION, 1,
                List.of("Nope", "Brief.", "2026-11-01"), reader));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.MISSION, 1, reader));
    }

    @Test
    void anAdminCanUpdateAMission() throws Exception {
        Path database = temporary.resolve("updated.db");
        repository(database).initialize();
        SqliteContentService service = service(database);
        service.initialize();

        service.update(ContentType.MISSION, 1,
                List.of("Operation Sandtrap", "Rewritten brief.", "1970-11-03"), admin);

        assertEquals("Rewritten brief.",
                service.read(ContentType.MISSION, 1, reader).values().get(1));
    }

    /* a mission may have zero or more briefs */

    private SqliteContentService withMissionsAndBriefs(Path database) throws Exception {
        repository(database).initialize();
        SqliteContentService service = service(database);
        service.initialize();
        service.add(ContentType.BRIEF, List.of("Pouch notes", "1970-11-04", "What the courier carried."), admin);
        service.add(ContentType.BRIEF, List.of("Rome follow-up", "1970-11-09", "Where the courier went."), admin);
        service.add(ContentType.BRIEF, List.of("Unrelated", "1975-08-13", "Belongs to no mission."), admin);
        return service;
    }

    @Test
    void aMissionStartsWithNoBriefs() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("nobriefs.db"));

        assertEquals(List.of(), service.briefsFor(1, reader));
    }

    @Test
    void anAttachedBriefIsListedForItsMission() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("oneBrief.db"));

        service.attachBrief(1, 1, admin);

        List<ContentEntry> briefs = service.briefsFor(1, reader);
        assertEquals(1, briefs.size());
        assertEquals("Pouch notes", briefs.get(0).values().get(0));
    }

    @Test
    void oneMissionMayHaveSeveralBriefs() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("manyBriefs.db"));

        service.attachBrief(1, 1, admin);
        service.attachBrief(1, 2, admin);

        assertEquals(2, service.briefsFor(1, reader).size());
        assertEquals(List.of(), service.briefsFor(2, reader));
    }

    @Test
    void attachingABriefAgainMovesItRatherThanCopyingIt() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("moved.db"));
        service.attachBrief(1, 1, admin);

        service.attachBrief(2, 1, admin);

        assertEquals(List.of(), service.briefsFor(1, reader));
        assertEquals(1, service.briefsFor(2, reader).size());
    }

    @Test
    void aMissionThatDoesNotExistHasNoBriefs() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("unknown.db"));

        assertEquals(List.of(), service.briefsFor(404, reader));
    }

    @Test
    void aBriefOutlivesTheMissionItWasAttachedTo() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("outlives.db"));
        service.attachBrief(1, 1, admin);

        service.delete(ContentType.MISSION, 1, admin);

        assertEquals("Pouch notes", service.read(ContentType.BRIEF, 1, reader).values().get(0));
        assertEquals(List.of(), service.briefsFor(1, reader));
    }

    @Test
    void aStandardUserMayReadBriefsForAMissionButNotAttachThem() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("briefRoles.db"));

        assertEquals(List.of(), service.briefsFor(1, reader));
        assertThrows(ProgramControlException.class, () -> service.attachBrief(1, 1, reader));
    }

    @Test
    void attachingAnUnknownBriefOrMissionIsRefused() throws Exception {
        SqliteContentService service = withMissionsAndBriefs(temporary.resolve("badLink.db"));

        assertThrows(ProgramControlException.class, () -> service.attachBrief(404, 1, admin));
        assertThrows(ProgramControlException.class, () -> service.attachBrief(1, 404, admin));
    }
}
