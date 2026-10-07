import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** The menu, control layer, search service, and repo working together. */
public class MenuIntegrationTest {

    private static final Mission SANDTRAP = new Mission(1, "Operation Sandtrap",
            "Bug the diplomatic pouch during the layover in Rome.",
            LocalDate.of(1970, 11, 3));
    private static final Mission WARSAW = new Mission(2, "The Warsaw Whisper",
            "Intercept correspondence from the underground press.",
            LocalDate.of(1975, 8, 12));

    private final MissionRepository twoMissions = () -> List.of(SANDTRAP, WARSAW);

    /** Builds the real stack on top of the given repo and runs the menu. **/
    private String runMenu(MissionRepository repository, String input) {
        ProgramControl control = new ProgramControlImpl(
                new UnusedFileHandler(),
                (text, key) -> text,
                new MissionSearchService(repository),
                repository);
        UserInterface ui = new UserInterface(control);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ui.runMenu(new Scanner(input), new PrintStream(buffer));
        return buffer.toString();
    }

    @Test
    void listShowsRepositoryMissionsInOrder() {
        String output = runMenu(twoMissions, "1\n4\n");

        assertTrue(output.contains("1. Operation Sandtrap"));
        assertTrue(output.contains("2. The Warsaw Whisper"));
    }

    @Test
    void readShowsTheChosenMissionFromTheRepository() {
        String output = runMenu(twoMissions, "2\n2\n4\n");

        assertTrue(output.contains("The Warsaw Whisper (1975-08-12)"));
        assertTrue(output.contains("Intercept correspondence from the underground press."));
    }

    @Test
    void searchIgnoreCaseAndShowsOnlyMatchingMissions() {
        String output = runMenu(twoMissions, "3\nDIPLOMATIC\n4\n");

        assertTrue(output.contains("1. Operation Sandtrap"));
        assertFalse(output.contains("Warsaw"));
    }

    @Test
    void searchOnlyLooksAtBriefsNotTitles() {
        String output = runMenu(twoMissions, "3\nWarsaw\n4\n");

        assertTrue(output.contains("No matches found."));
    }

    @Test
    void blankSearchShowsTheSearchServiceMessageAndKeepsRunning() {
        String output = runMenu(twoMissions, "3\n   \n4\n");

        assertTrue(output.contains("Error: Search phrase cannot be empty."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void repositoryFailureOnListIsShownAndMenuKeepsRunning() {
        MissionRepository broken = () -> {
            throw new MissionRepositoryException("Mission database is not connected.");
        };

        String output = runMenu(broken, "1\n4\n");

        assertTrue(output.contains("Error: Mission database is not connected."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void repositoryFailureOnSearchIsShownAndMenuKeepsRunning() {
        MissionRepository broken = () -> {
            throw new MissionRepositoryException("Mission database is not connected.");
        };

        String output = runMenu(broken, "3\nplan\n4\n");

        assertTrue(output.contains("Error: Mission search could not be completed."));
        assertTrue(output.contains("Goodbye."));
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
