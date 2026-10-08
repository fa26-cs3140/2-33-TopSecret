import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a real SQLite file in a temp folder, up through search and control. */
class SqliteMissionRepositoryIntegrationTest {

    private static final String TSV_NAME = "missions.tsv";

    private static final String THREE_MISSIONS =
            "Title\tDate\tText\n"
            + "Operation Sandtrap\t1970-11-03\tBug the diplomatic pouch in Rome.\n"
            + "The Warsaw Whisper\t1975-08-12\tIntercept the underground press.\n"
            + "The Beirut Wire\t1984-03-16\tTrack the kidnappers through West Beirut.\n";

    private SqliteMissionRepository repository(Path folder, String tsv) {
        return new SqliteMissionRepository(
                new CannedTsvFileHandler(tsv), folder.resolve("missions.db"), TSV_NAME);
    }

    /* the database on its own */

    @Test
    void initializeCreatesTheTableAndImportsTheTsv(@TempDir Path folder)
            throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);

        repository.initialize();

        assertEquals(3, repository.getAllMissions().size());
    }

    @Test
    void missionsComeBackInFileOrder(@TempDir Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);
        repository.initialize();

        List<String> titles = repository.getAllMissions().stream()
                .map(Mission::getTitle)
                .toList();

        assertEquals(List.of("Operation Sandtrap", "The Warsaw Whisper", "The Beirut Wire"), titles);
    }

    @Test
    void idsAreSequentialAndMatchFileOrder(@TempDir Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);
        repository.initialize();

        List<Mission> missions = repository.getAllMissions();

        assertEquals(1, missions.get(0).getId());
        assertEquals(2, missions.get(1).getId());
        assertEquals(3, missions.get(2).getId());
    }

    @Test
    void allFourFieldsSurviveTheRoundTrip(@TempDir Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);
        repository.initialize();

        Mission first = repository.getAllMissions().get(0);

        assertEquals(1, first.getId());
        assertEquals("Operation Sandtrap", first.getTitle());
        assertEquals("Bug the diplomatic pouch in Rome.", first.getBrief());
        assertEquals(LocalDate.of(1970, 11, 3), first.getDate());
    }

    @Test
    void briefsAreStoredAsPlainTextNotCiphered(@TempDir Path folder)
            throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);
        repository.initialize();

        assertEquals("Bug the diplomatic pouch in Rome.",
                repository.getAllMissions().get(0).getBrief());
    }

    @Test
    void initializingTwiceDoesNotDuplicateRecords(@TempDir Path folder)
            throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);

        repository.initialize();
        repository.initialize();

        assertEquals(3, repository.getAllMissions().size());
    }

    @Test
    void aSecondRepositoryOnTheSameFileSeesTheSameRecords(@TempDir Path folder)
            throws MissionRepositoryException {
        repository(folder, THREE_MISSIONS).initialize();

        SqliteMissionRepository reopened = repository(folder, THREE_MISSIONS);

        assertEquals(3, reopened.getAllMissions().size());
    }

    @Test
    void headerOnlyTsvLeavesTheTableEmpty(@TempDir Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = repository(folder, "Title\tDate\tText\n");

        repository.initialize();

        assertTrue(repository.getAllMissions().isEmpty());
        assertEquals(0, repository.missionCount());
    }

    @Test
    void initializeCreatesTheDatabaseFolderIfItIsMissing(@TempDir Path folder)
            throws MissionRepositoryException {
        Path nested = folder.resolve("nested").resolve("deeper");
        SqliteMissionRepository repository = new SqliteMissionRepository(
                new CannedTsvFileHandler(THREE_MISSIONS),
                nested.resolve("missions.db"),
                TSV_NAME);

        repository.initialize();

        assertEquals(3, repository.getAllMissions().size());
    }

    /* failure handling */

    @Test
    void missingImportFileIsReportedAsARepositoryError(@TempDir Path folder) {
        SqliteMissionRepository repository = repository(folder, null);

        MissionRepositoryException thrown =
                assertThrows(MissionRepositoryException.class, repository::initialize);

        assertTrue(thrown.getMessage().contains("could not be read"));
    }

    @Test
    void malformedImportFileLeavesTheTableEmpty(@TempDir Path folder)
            throws MissionRepositoryException {
        SqliteMissionRepository repository =
                repository(folder, "Title\tDate\tText\nBroken Row\n");

        assertThrows(MissionRepositoryException.class, repository::initialize);
        assertEquals(0, repository.missionCount());
    }

    @Test
    void readingBeforeInitializeIsReportedAsARepositoryError(@TempDir Path folder) {
        SqliteMissionRepository repository = repository(folder, THREE_MISSIONS);

        assertThrows(MissionRepositoryException.class, repository::getAllMissions);
    }

    /* the real project data, end to end */

    @Test
    void theProjectTsvImportsEveryRecord(@TempDir Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = projectRepository(folder);

        repository.initialize();

        assertEquals(100, repository.getAllMissions().size());
    }

    @Test
    void theSearchServiceFindsMissionsInTheRealDatabase(@TempDir Path folder)
            throws MissionRepositoryException, MissionSearchException {
        SqliteMissionRepository repository = projectRepository(folder);
        repository.initialize();
        MissionSearch search = new MissionSearchService(repository);

        List<Mission> matches = search.search("quantum computer");

        assertFalse(matches.isEmpty());
        assertTrue(matches.get(0).getBrief().toLowerCase().contains("quantum computer"));
    }

    @Test
    void theSearchServiceIgnoresCaseAgainstTheRealDatabase(@TempDir Path folder)
            throws MissionRepositoryException, MissionSearchException {
        SqliteMissionRepository repository = projectRepository(folder);
        repository.initialize();
        MissionSearch search = new MissionSearchService(repository);

        assertEquals(search.search("quantum computer").size(),
                search.search("QUANTUM COMPUTER").size());
    }

    @Test
    void aSearchWithNoMatchesComesBackEmpty(@TempDir Path folder)
            throws MissionRepositoryException, MissionSearchException {
        SqliteMissionRepository repository = projectRepository(folder);
        repository.initialize();
        MissionSearch search = new MissionSearchService(repository);

        assertTrue(search.search("zzzz no such phrase zzzz").isEmpty());
    }

    @Test
    void theControlLayerListsTheRealDatabase(@TempDir Path folder)
            throws MissionRepositoryException, ProgramControlException {
        ProgramControl control = projectControl(folder);

        assertEquals(100, control.listMissions().size());
    }

    @Test
    void theControlLayerSearchesTheRealDatabase(@TempDir Path folder)
            throws MissionRepositoryException, ProgramControlException {
        ProgramControl control = projectControl(folder);

        assertFalse(control.searchMissions("quantum computer").isEmpty());
    }

    @Test
    void theControlLayerListsMissionsInIdOrder(@TempDir Path folder)
            throws MissionRepositoryException, ProgramControlException {
        List<Mission> missions = projectControl(folder).listMissions();

        for (int index = 0; index < missions.size(); index++) {
            assertEquals(index + 1, missions.get(index).getId());
        }
    }

    /* Real FileHandler against the real project TSV, database in a temp folder. */
    private SqliteMissionRepository projectRepository(Path folder) {
        return new SqliteMissionRepository(
                new FileHandlerImpl(),
                folder.resolve("missions.db"),
                SqliteMissionRepository.DEFAULT_TSV);
    }

    /* The same wiring TopSecret.main builds, with a throwaway database. */
    private ProgramControl projectControl(Path folder) throws MissionRepositoryException {
        SqliteMissionRepository repository = projectRepository(folder);
        repository.initialize();
        return new ProgramControlImpl(
                new FileHandlerImpl(),
                (text, key) -> text,
                new MissionSearchService(repository),
                repository);
    }

    /* Hands back one canned TSV body, or null to simulate a missing file. */
    private static class CannedTsvFileHandler implements FileHandler {

        private final String tsv;

        CannedTsvFileHandler(String tsv) {
            this.tsv = tsv;
        }

        @Override
        public String getFileList() {
            return "";
        }

        @Override
        public String readFile(int fileNumber) {
            return null;
        }

        @Override
        public String readKey(String keyFileName) {
            return null;
        }

        @Override
        public String readText(String relativePath) {
            return TSV_NAME.equals(relativePath) ? tsv : null;
        }
    }
}
