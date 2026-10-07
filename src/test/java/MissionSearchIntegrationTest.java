import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the repository, search service, and control layer working together. */
class MissionSearchIntegrationTest {

    @Test
    void programControlReturnsMatchingMissionsFromRepository() throws ProgramControlException {
        Mission first = new Mission(1, "Operation Sandtrap",
                "Bug the diplomatic pouch during the layover in Rome.",
                LocalDate.of(1970, 11, 3));
        Mission second = new Mission(2, "The Warsaw Whisper",
                "Intercept correspondence from the underground press.",
                LocalDate.of(1975, 8, 12));
        Mission third = new Mission(3, "The Beirut Wire",
                "Track the kidnappers through the backstreets of West Beirut.",
                LocalDate.of(1984, 3, 16));

        MissionRepository repository = () -> List.of(first, second, third);
        MissionSearch search = new MissionSearchService(repository);
        ProgramControl control = new ProgramControlImpl(
                new UnusedFileHandler(), (text, key) -> text, search);

        List<Mission> matches = control.searchMissions("THE");

        assertEquals(List.of(first, second, third), matches);
    }

    @Test
    void programControlReturnsNoMatchesAsAnEmptyList() throws ProgramControlException {
        Mission mission = new Mission(1, "Operation Sandtrap",
                "Bug the diplomatic pouch during the layover in Rome.",
                LocalDate.of(1970, 11, 3));
        MissionRepository repository = () -> List.of(mission);
        ProgramControl control = new ProgramControlImpl(
                new UnusedFileHandler(),
                (text, key) -> text,
                new MissionSearchService(repository));

        List<Mission> matches = control.searchMissions("submarine");

        assertTrue(matches.isEmpty());
    }

    private static class UnusedFileHandler implements FileHandler {
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
    }
}
