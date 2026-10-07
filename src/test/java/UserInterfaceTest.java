import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Tests for the UI layer. Everything runs against the fake, so none of this
 * touches the data folder or the cipher.
 */
public class UserInterfaceTest {

    private FakeProgramControl control;
    private UserInterface ui;

    @BeforeEach
    public void setUp() {
        control = new FakeProgramControl();
        ui = new UserInterface(control);
    }

    /* listing files */

    @Test
    public void noArguments_listsFilesWithTwoDigitNumbers() {
        control.setFiles("carnivore.cip", "cointelpro.cip");

        String output = ui.run(new String[]{});

        assertEquals("01 carnivore.cip\n02 cointelpro.cip\n", output);
    }

    @Test
    public void tenthFile_isNumberedTen() {
        control.setFiles("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

        String output = ui.run(new String[]{});

        assertTrue(output.contains("09 i\n"), "ninth file should be 09, got:\n" + output);
        assertTrue(output.contains("10 j\n"), "tenth file should be 10, got:\n" + output);
    }

    @Test
    public void noFilesAvailable_saysSo() {
        control.setFiles();

        String output = ui.run(new String[]{});

        assertEquals("No files are available.\n", output);
    }

    @Test
    public void listNamesAreNotChanged() {
        control.setFiles("carnivore.cip");

        String output = ui.run(new String[]{});

        assertEquals("01 carnivore.cip\n", output);
    }

    /* showing one file */

    @Test
    public void validNumber_showsFileContents() {
        control.setContents("the quick brown fox");

        String output = ui.run(new String[]{"01"});

        assertEquals("the quick brown fox\n", output);
    }

    @Test
    public void leadingZerosAreOptional() {
        control.setContents("text");

        ui.run(new String[]{"1"});

        assertEquals(1, control.getLastNumber());
    }

    @Test
    public void leadingZerosAreStripped() {
        control.setContents("text");

        ui.run(new String[]{"007"});

        assertEquals(7, control.getLastNumber());
    }

    @Test
    public void argumentsAreTrimmed() {
        control.setContents("text");

        ui.run(new String[]{"  02  "});

        assertEquals(2, control.getLastNumber());
    }

    @Test
    public void oneArgument_usesTheDefaultKey() {
        control.setContents("text");

        ui.run(new String[]{"01"});

        assertTrue(control.wasDefaultKeyUsed(), "one argument should use the default key");
    }

    /* second argument, the alternate key */

    @Test
    public void secondArgument_isPassedToControlLayerUnchanged() {
        control.setContents("text");

        ui.run(new String[]{"01", "backup.txt"});

        assertEquals("backup.txt", control.getLastKeyName());
        assertEquals(1, control.getLastNumber());
    }

    @Test
    public void secondArgument_stopsTheDefaultKeyBeingUsed() {
        control.setContents("text");

        ui.run(new String[]{"01", "backup.txt"});

        assertTrue(!control.wasDefaultKeyUsed(), "a named key should replace the default");
    }

    /* things going wrong */

    @Test
    public void nonNumericArgument_showsErrorAndDoesNotThrow() {
        String output = ui.run(new String[]{"abc"});

        assertEquals("Error: 'abc' is not a file number.\n"
                + "Run with no arguments to see the list.\n", output);
    }

    @Test
    public void emptyArgument_showsError() {
        String output = ui.run(new String[]{""});

        assertEquals("Error: '' is not a file number.\n"
                + "Run with no arguments to see the list.\n", output);
    }

    @Test
    public void unknownNumber_showsTheControlLayerMessage() {
        control.failWith("There is no file numbered 99.");

        String output = ui.run(new String[]{"99"});

        assertEquals("Error: There is no file numbered 99.\n"
                + "Run with no arguments to see the list.\n", output);
    }

    @Test
    public void badKey_showsTheControlLayerMessage() {
        control.failWith("The key file missing.txt could not be read.");

        String output = ui.run(new String[]{"01", "missing.txt"});

        assertEquals("Error: The key file missing.txt could not be read.\n"
                + "Run with no arguments to see the list.\n", output);
    }

    @Test
    public void tooManyArguments_showsUsage() {
        String output = ui.run(new String[]{"01", "key.txt", "extra"});

        assertEquals("Usage: TopSecret [number] [keyname]\n", output);
    }

    @Test
    public void tooManyArguments_neverReachesTheControlLayer() {
        ui.run(new String[]{"01", "key.txt", "extra"});

        assertEquals(-1, control.getLastNumber());
    }

    @Test
    public void nullArguments_areTreatedAsNoArguments() {
        control.setFiles("carnivore.cip");

        String output = ui.run(null);

        assertEquals("01 carnivore.cip\n", output);
    }

    /* interactive menu */

    private String runMenuWith(String input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ui.runMenu(new Scanner(input), new PrintStream(buffer));
        return buffer.toString();
    }

    private Mission mission(int id, String title, String brief) {
        return new Mission(id, title, brief, LocalDate.of(2026, 1,1));
    }

    @Test
    public void menu_exitSaysGoodbye() {
        assertTrue(runMenuWith("4\n").contains("Goodbye."));
    }

    @Test
    public void menu_endOfInputStopsWithoutHanging() {
        assertTrue(runMenuWith("").contains("Choose:"));
    }

    @Test
    public void menu_invalidOptionShowsHintAndKeepsRunning() {
        String output = runMenuWith("9\n4\n");

        assertTrue(output.contains("Please enter 1-4."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    public void menu_isShownAgainAfterAnAction() {
        String output = runMenuWith("1\n4\n");

        assertTrue(output.split("Choose:", -1).length - 1 >= 2, "menu should be shown again after listing, got:\n"+ output);
    }

    @Test
    public void list_showsNumberedTitles() {
        control.setMissions(mission(1, "Alpha", "plan one"), mission(2, "Bravo", "plan two"));

        String output = runMenuWith("1\n4\n");

        assertTrue(output.contains("1. Alpha"));
        assertTrue(output.contains("2. Bravo"));
    }

    @Test
    public void list_showsControlErrorAndKeepsRunning() {
        control.failWith("Database is down.");

        String output = runMenuWith("1\n4\n");

        assertTrue(output.contains("Error: Database is down."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    public void read_showsTitleDateAndBrief() {
        control.setMissions(mission(1, "Alpha", "plan one"), mission(2, "Bravo", "plan two"));

        String output = runMenuWith("2\n2\n4\n");

        assertTrue(output.contains("Bravo (2026-01-01)"));
        assertTrue(output.contains("plan two"));
    }

    @Test
    public void read_nonNumericInputShowsMessage() {
        control.setMissions(mission(1, "Alpha", "plan one"));

        assertTrue(runMenuWith("2\nabc\n4\n").contains("'abc' is not a mission number."));
    }

    @Test
    public void read_outOfRangeNumbersShowMessage() {
        control.setMissions(mission(1, "Alpha", "plan one"));

        assertTrue(runMenuWith("2\n0\n4\n").contains("There is no mission numbered 0."));
        assertTrue(runMenuWith("2\n5\n4\n").contains("There is no mission numbered 5."));

    }

    @Test
    public void search_showsMatchingTitlesAndPassesQueryThrough() {
        control.setSearchResults(mission(1, "Alpha", "secret plan"));

        String output = runMenuWith("3\nplan\n4\n");

        assertTrue(output.contains("1. Alpha"));
        assertEquals("plan", control.getLastSearchQuery());
    }

    @Test
    public void search_saysSoWhenNothingMatches() {
        assertTrue(runMenuWith("3\nzzz\n4\n").contains("No matches found."));
    }

    @Test
    public void search_showsControlErrorAndKeepsRunning() {
        control.failWith("Search phrase cannot be empty.");

        String output = runMenuWith("3\n\n4\n");

        assertTrue(output.contains("Error: Search phrase cannot be empty."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    public void list_saysSoWhenThereAreNoMissions() {
        assertTrue(runMenuWith("1\n4\n").contains("No missions available."));
    }


    @Test
    public void menu_showsBannerOnceAndLoadingBeforeListing() {
        List<String> calls = new ArrayList<>();
        Effects recording = new Effects() {
            public void banner(PrintStream out) { calls.add("banner"); }
            public void loading(PrintStream out, String message) { calls.add("loading:" + message); }

        };
        ui = new UserInterface(control, recording);

        runMenuWith("1\n4\n");

        assertEquals(List.of("banner", "loading:Decrypting records"), calls);

    }

    @Test
    public void menu_loadingBeforeReadingNamesTheMission() {
        List<String> calls = new ArrayList<>();
        Effects recording = new Effects() {
            public void banner(PrintStream out) {  }
            public void loading(PrintStream out, String message) { calls.add(message); }
        };
        control.setMissions(mission(1, "Alpha", "Plan one"));
        ui = new UserInterface(control, recording);

        runMenuWith("2\n1\n4\n");
        assertEquals(List.of("Decrypting mission 1"), calls);
    }
}
