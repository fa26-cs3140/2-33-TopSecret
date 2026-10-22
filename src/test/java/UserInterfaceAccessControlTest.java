import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the menu is allowed to draw for the signed-in user. */
class UserInterfaceAccessControlTest {

    private UserInterface userInterface() {
        ProgramControl control = new ProgramControlImpl(
                new FakeFileHandler(),
                (text, key) -> text,
                query -> List.of(),
                List::of);
        return new UserInterface(control);
    }

    private String runMenuWith(UserInterface ui, Session session, String input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        ui.runMenu(new Scanner(input), out, session);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void withNoSessionTheAdminOptionsStayHidden() {
        assertFalse(userInterface().showsAdminOptions());
    }

    @Test
    void anAdminSessionShowsTheAdminOptions() {
        UserInterface ui = userInterface();

        runMenuWith(ui, new Session("boss", Role.ADMIN), "4\n");

        assertTrue(ui.showsAdminOptions());
    }

    @Test
    void aStandardSessionHidesTheAdminOptions() {
        UserInterface ui = userInterface();

        runMenuWith(ui, new Session("agent", Role.STANDARD), "4\n");

        assertFalse(ui.showsAdminOptions());
    }

    @Test
    void theMenuStillRunsAndExitsWithASession() {
        String output = runMenuWith(userInterface(), new Session("boss", Role.ADMIN), "4\n");

        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void theMenuStillRunsForAStandardUser() {
        String output = runMenuWith(userInterface(), new Session("agent", Role.STANDARD), "4\n");

        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void aSessionPassedOnceIsRememberedAfterTheMenuEnds() {
        UserInterface ui = userInterface();

        runMenuWith(ui, new Session("boss", Role.ADMIN), "4\n");
        String second = runMenuWith(ui, new Session("agent", Role.STANDARD), "4\n");

        assertTrue(second.contains("Goodbye."));
        assertFalse(ui.showsAdminOptions());
    }
}
