import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TopSecret {

    public static void main(String[] args) {
        FileHandler fileHandler = new FileHandlerImpl();
        Cipher cipher = new SubstitutionCipher(fileHandler);
        AuthenticationService auth = new AuthenticationService(new FileCredentialStore(fileHandler, cipher));

        // one repository for both the search service and the control layer, so
        // a list and a search can't disagree
        SqliteMissionRepository missions = new SqliteMissionRepository(fileHandler);
        try {
            missions.initialize();
        } catch (MissionRepositoryException exception) {
            System.out.println("Error: " + exception.getMessage());
            return;
        }

        MissionSearch search = new MissionSearchService(missions);
        ProgramControl control = new ProgramControlImpl(fileHandler, cipher, search, missions);
        UserInterface ui = new UserInterface(control);

        // briefs are UTF-8, so the console has to be too or accented place
        // names come out as question marks
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
        run(args, in, out, auth, ui);
    }

    static void run(String[] args, Scanner in, PrintStream out, AuthenticationService auth, UserInterface ui) {
        if (args != null && args.length == 1 && "--change-password".equals(args[0])) {
            changePassword(in, out, auth);
            return;
        }
        if (args != null && args.length > 0) {
            out.println("Usage: TopSecret [--change-password]");
            return;
        }

        if (!auth.hasCredentialFile()) {
            createFirstCredentials(in, out, auth);
            return;
        }

        try {
            auth.verifyStoredCredentialsAreValid();
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
            return;
        }

        if (!login(in, out, auth)) {
            return;
        }
        ui.runMenu(in, out);
    }

    private static void createFirstCredentials(Scanner in, PrintStream out, AuthenticationService auth) {
        out.println("No credential file found. Create a username and password.");
        String username = prompt(in, out, "New username: ");
        String password = prompt(in, out, "New password: ");
        if (username == null || password == null) {
            out.println("Credential setup cancelled.");
            return;
        }
        try {
            auth.createCredentials(username, password);
            out.println("Credential file created. Restart the program to log in.");
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
        }
    }

    private static boolean login(Scanner in, PrintStream out, AuthenticationService auth) {
        String username = prompt(in, out, "Username: ");
        String password = prompt(in, out, "Password: ");
        if (username == null || password == null) {
            out.println("Login cancelled.");
            return false;
        }
        try {
            if (auth.authenticate(username, password)) {
                return true;
            }
            out.println("Login failed.");
            return false;
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
            return false;
        }
    }

    private static void changePassword(Scanner in, PrintStream out, AuthenticationService auth) {
        if (!auth.hasCredentialFile()) {
            createFirstCredentials(in, out, auth);
            return;
        }

        String username = prompt(in, out, "Username: ");
        String currentPassword = prompt(in, out, "Current password: ");
        String newPassword = prompt(in, out, "New password: ");
        String confirmation = prompt(in, out, "Confirm new password: ");
        if (username == null || currentPassword == null || newPassword == null || confirmation == null) {
            out.println("Password change cancelled.");
            return;
        }
        try {
            auth.changePassword(username, currentPassword, newPassword, confirmation);
            out.println("Password changed.");
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
        }
    }

    private static String prompt(Scanner in, PrintStream out, String message) {
        out.print(message);
        if (!in.hasNextLine()) {
            return null;
        }
        return in.nextLine().trim();
    }
}
