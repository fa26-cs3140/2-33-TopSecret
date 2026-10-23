import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ContentMenu {
    private final ContentService content;
    private final AccessControl access = new AccessControl();
    private final AuthenticationService authentication;

    public ContentMenu(ContentService content) {
        this(content, null);
    }

    public ContentMenu(ContentService content, AuthenticationService authentication) {
        this.content = content;
        this.authentication = authentication;
    }

    public void run(Scanner in, PrintStream out, Session session) {
        if (!access.canReadContent(session)) {
            out.println("Please log in first.");
            return;
        }
        while (true) {
            out.println("\n1) Briefs\n2) Agents\n3) Facilities\n4) Exit");
            if (access.canViewAuditLog(session)) {
                out.println("5) Review audit log");
            }
            if (authentication != null && access.canCreateUsers(session)) {
                out.println("6) Create user\n7) Promote user\n8) Demote user");
            }
            String choice = prompt(in, out, "Choose: ");
            if (choice == null || choice.equals("4")) {
                out.println("Goodbye.");
                return;
            }
            try {
                switch (choice) {
                    case "1": browse(ContentType.BRIEF, in, out, session); break;
                    case "2": browse(ContentType.AGENT, in, out, session); break;
                    case "3": browse(ContentType.FACILITY, in, out, session); break;
                    case "6": case "7": case "8":
                        manageUser(choice, in, out, session);
                        break;
                    case "5":
                        if (!access.canViewAuditLog(session)) {
                            out.println("Access denied.");
                            break;
                        }
                        List<AuditLogEntry> entries = content.audit(session);
                        if (entries.isEmpty()) {
                            out.println("Audit log is empty.");
                        }
                        for (AuditLogEntry entry : entries) {
                            out.println(entry.getTimestamp() + " | " + entry.getUsername() + " | "
                                    + entry.getEventType() + " | " + entry.getContentType() + " #" + entry.getContentId());
                        }
                        break;
                    default: out.println("Please choose a displayed option.");
                }
            } catch (ProgramControlException exception) {
                out.println("Error: " + exception.getMessage());
            }
        }
    }

    private void manageUser(String choice, Scanner in, PrintStream out, Session session) {
        if (authentication == null || !access.canCreateUsers(session)) {
            out.println("Access denied.");
            return;
        }
        try {
            if (!choice.equals("6")) {
                out.println("Users: " + String.join(", ", authentication.listUsernames()));
            }
            String username = prompt(in, out, "Username: ");
            if (username == null) {
                return;
            }
            switch (choice) {
                case "6": authentication.createUser(username); break;
                case "7": authentication.promote(session.getUsername(), username); break;
                case "8": authentication.demote(session.getUsername(), username); break;
                default: throw new IllegalArgumentException("Unknown user action");
            }
            out.println(choice.equals("6") ? "User created. They choose their password at first login." : "Role updated.");
        } catch (CredentialException exception) {
            out.println("Error: " + exception.getMessage());
        }
    }

    private void browse(ContentType type, Scanner in, PrintStream out, Session session) throws ProgramControlException {
        while (true) {
            out.println("\n" + type.table + "\n1) List entries\n2) Read entry\n4) Back");
            if (access.canCreateContent(session)) {
                out.println("3) Add entry\n5) Update entry");
            }
            String choice = prompt(in, out, "Choose: ");
            if (choice == null || choice.equals("4")) {
                return;
            }
            try {
                switch (choice) {
                    case "1": showList(type, out, session); break;
                    case "2":
                        String value = prompt(in, out, "Entry ID: ");
                        if (value == null) {
                            return;
                        }
                        ContentEntry entry = content.read(type, Integer.parseInt(value), session);
                        for (int index = 0; index < type.fields.size(); index++) {
                            out.println(type.fields.get(index) + ": " + entry.values().get(index));
                        }
                        break;
                    case "3":
                        if (!access.canCreateContent(session)) {
                            out.println("Access denied.");
                            break;
                        }
                        List<String> values = promptValues(type, in, out, session);
                        if (values == null) {
                            return;
                        }
                        content.add(type, values, session);
                        out.println("Entry added.");
                        showList(type, out, session);
                        break;
                    case "5":
                        if (!access.canCreateContent(session)) {
                            out.println("Access denied.");
                            break;
                        }
                        String id = prompt(in, out, "Entry ID: ");
                        if (id == null) {
                            return;
                        }
                        List<String> replacements = promptValues(type, in, out, session);
                        if (replacements == null) {
                            return;
                        }
                        content.update(type, Integer.parseInt(id), replacements, session);
                        out.println("Entry updated.");
                        showList(type, out, session);
                        break;
                    default: out.println("Please choose a displayed option.");
                }
            } catch (NumberFormatException exception) {
                out.println("Enter a numeric entry ID.");
            } catch (ProgramControlException exception) {
                out.println("Error: " + exception.getMessage());
            }
        }
    }

    private List<String> promptValues(ContentType type, Scanner in, PrintStream out,
                                      Session session) throws ProgramControlException {
        if (type == ContentType.AGENT) {
            out.println("Choose an existing facility ID:");
            showList(ContentType.FACILITY, out, session);
        }
        List<String> values = new ArrayList<>();
        for (String field : type.fields) {
            String input = prompt(in, out, field
                    + (field.contains("date") ? " (YYYY-MM-DD; death may be blank)" : "")
                    + ": ");
            if (input == null) {
                out.println("Entry cancelled.");
                return null;
            }
            values.add(input);
        }
        return values;
    }

    private void showList(ContentType type, PrintStream out, Session session) throws ProgramControlException {
        List<ContentEntry> entries = content.list(type, session);
        if (entries.isEmpty()) {
            out.println("No entries available.");
        }
        for (ContentEntry entry : entries) {
            out.println(entry.id() + ". " + entry.values().get(0));
        }
    }

    private String prompt(Scanner in, PrintStream out, String message) {
        out.print(message);
        return in.hasNextLine() ? in.nextLine().trim() : null;
    }
}
