import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
