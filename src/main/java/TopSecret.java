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
        // the audit log table lives in the same database; the control layer
        // will record reads and creates into this once they are wired up
        SqliteAuditLogRepository auditLog = new SqliteAuditLogRepository();
        try {
            auditLog.initialize();
        } catch (AuditLogException exception) {
            System.out.println("Error: " + exception.getMessage());
            return;
        }

        MissionSearch search = new MissionSearchService(missions);
        ProgramControl control = new ProgramControlImpl(fileHandler, cipher, search, missions);
        UserInterface ui = new UserInterface(control, new AnimatedEffects());

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

        String username;
        try {
            if (!auth.hasAccounts()) {
                username = createFirstAccount(in, out, auth);
            } else {
                auth.verifyStoredCredentialsAreValid();
                username = login(in, out, auth);
            }
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
            return;
        }
        if (username == null) {
            return;
        }

        Session session;
        try {
            session = auth.sessionFor(username);
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
            return;
        }
        out.println("Signed in as " + session.getUsername() + ", role " + session.getRole() + ".");

        // the session carries who is logged in and what they may do. the menu
        // asks AccessControl which options to draw, the audit log takes the
        // username off it
        ui.runMenu(in, out, session);
    }

    // returns the new username, or null if setup was cancelled
    private static String createFirstAccount(Scanner in, PrintStream out, AuthenticationService auth)
            throws CredentialException {
        out.println("No accounts found. Create the first account.");
        String username = prompt(in, out, "New username: ");
        String password = prompt(in, out, "New password: ");
        if (username == null || password == null) {
            out.println("Account setup cancelled.");
            return null;
        }
        auth.createCredentials(username, password);
        out.println("Account created. You are logged in as " + username + ".");
        return username;
    }

    // returns the logged-in username, or null if login failed or was cancelled
    private static String login(Scanner in, PrintStream out, AuthenticationService auth)
            throws CredentialException {
        String username = prompt(in, out, "Username: ");
        if (username == null) {
            out.println("Login cancelled.");
            return null;
        }
        if (auth.needsPassword(username)) {
            return createFirstPassword(in, out, auth, username);
        }
        String password = prompt(in, out, "Password: ");
        if (password == null) {
            out.println("Login cancelled.");
            return null;
        }
        if (auth.authenticate(username, password)) {
            return username;
        }
        out.println("Login failed.");
        return null;
    }

    // a new user picks their own password the first time they log in
    private static String createFirstPassword(Scanner in, PrintStream out, AuthenticationService auth,
                                              String username) throws CredentialException {
        out.println("Welcome, " + username + ". Create a password for your account.");
        String password = prompt(in, out, "New password: ");
        String confirmation = prompt(in, out, "Confirm new password: ");
        if (password == null || confirmation == null) {
            out.println("Login cancelled.");
            return null;
        }
        auth.setInitialPassword(username, password, confirmation);
        return username;
    }

    private static void changePassword(Scanner in, PrintStream out, AuthenticationService auth) {
        try {
            if (!auth.hasAccounts()) {
                createFirstAccount(in, out, auth);
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