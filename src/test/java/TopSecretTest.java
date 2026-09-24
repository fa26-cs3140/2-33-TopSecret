import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopSecretTest {

    private PrintStream originalOutput;
    private ByteArrayOutputStream capturedOutput;

    @BeforeEach
    void captureConsoleOutput() {
        originalOutput = System.out;
        capturedOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreConsoleOutput() {
        System.setOut(originalOutput);
    }

    @Test
    void mainWithNoArgumentsListsAvailableFiles() {
        TopSecret.main(new String[]{});

        assertEquals("01 carnivore.cip\n02 cointelpro.cip\n", output());
    }

    @Test
    void mainWithFileNumberDisplaysDecipheredContents() {
        TopSecret.main(new String[]{"01"});

        assertTrue(output().startsWith("Carnivore, later renamed DCS1000"));
    }

    @Test
    void mainWithMissingFileDisplaysErrorWithoutExiting() {
        TopSecret.main(new String[]{"99"});

        assertEquals("Error: File not found: 99\n"
                + "Run with no arguments to see the list.\n", output());
    }

    private String output() {
        return capturedOutput.toString(StandardCharsets.UTF_8);
    }
}
