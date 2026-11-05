package view;

import controller.ContentController;
import controller.ProgramControlException;
import model.AccessControl;
import model.AuthenticationService;
import model.ContentEntry;
import model.ContentService;
import model.ContentType;
import model.CredentialException;
import model.MessageService;
import model.Role;
import model.Session;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ContentMenu {
    private final ContentService content;
    private final ContentController navigation;
    private final AccessControl access = new AccessControl();
    private final AuthenticationService authentication;
    private final MessageMenu messages;

    public ContentMenu(ContentService content) {
        this(content, null);
    }
    public ContentMenu(ContentService content, AuthenticationService authentication) {
        this(content, authentication, null);
    }

    /** Messaging is optional: with no service, the message options are not offered. */
    public ContentMenu(ContentService content, AuthenticationService authentication,
                       MessageService messageService) {
        this.content = content;
        this.navigation = new ContentController(content, authentication);
        this.authentication = authentication;
        this.messages = messageService == null ? null : new MessageMenu(messageService);
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
            if (messages != null && access.canSendMessage(session)) {
                out.println("9) Send a message\n10) My messages");
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
                    case "9":
                        if (messages != null && access.canSendMessage(session)) {
                            messages.send(in, out, session);
                        } else {
                            out.println("Please choose a displayed option.");
                        }
                        break;
                    case "10":
                        if (messages != null && access.canReadMessages(session)) {
                            messages.showInbox(out, session);
                        } else {
                            out.println("Please choose a displayed option.");
                        }
                        break;
                    case "6": case "7": case "8":
                        manageUser(choice, in, out, session);
                        break;
                    case "5":
                        if (!access.canViewAuditLog(session)) {
                            out.println("Access denied.");
                            break;
                        }
                        out.println(ContentText.audit(navigation.audit(session)));
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
            out.println("\n" + type.table + "\n1) List entries\n2) Read entry");
            if (access.canCreateContent(session)) {
                out.println("3) Add entry");
            }
            out.println("4) Back");
            if (access.canCreateContent(session)) {
                out.println("5) Update entry");
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
                        ContentEntry entry = navigation.read(type, Integer.parseInt(value), session);
                        out.print(ContentText.record(type, entry));
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
        List<ContentEntry> entries = navigation.list(type, session);
        if (entries.isEmpty()) {
            out.println("No entries available.");
        }
        for (ContentEntry entry : entries) {
            out.println(ContentText.title(entry));
        }
    }

    private String prompt(Scanner in, PrintStream out, String message) {
        out.print(message);
        return in.hasNextLine() ? in.nextLine().trim() : null;
    }
}
