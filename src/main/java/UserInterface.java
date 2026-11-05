import java.util.List;
import java.io.PrintStream;
import java.util.Scanner;

/*
 * Asks the control layer for what it needs and draws the menu.
 * Doesn't exit and doesn't touch files.
 *
 * Spec is in docs/userinterface.txt.
 */
public class UserInterface {

    private final ProgramControl control;
    private final Effects effects;
    private final AccessControl access = new AccessControl();
    private Session session;
    private ContentMenu contentMenu;

    public UserInterface(ProgramControl control, Effects effects, ContentService content) {
        this(control, effects, content, null);
    }

    public UserInterface(ProgramControl control, Effects effects, ContentService content, AuthenticationService authentication) {
        this(control, effects, content, authentication, null);
    }

    /** Messaging is optional: with no service, the message options are not offered. */
    public UserInterface(ProgramControl control, Effects effects, ContentService content,
                         AuthenticationService authentication, MessageService messages) {
        this(control, effects);
        this.contentMenu = new ContentMenu(content, authentication, messages);
    }

    public UserInterface(ProgramControl control) {
        this(control, Effects.NONE);
    }

    public UserInterface(ProgramControl control, Effects effects) {
        this.control = control;
        this.effects = effects;
    }

    /*
     * Member A added this. The session says who is logged in and what they may
     * do; ask access.canX(session) before drawing an option that needs a role.
     * The two-argument version below still works and runs with no session,
     * which AccessControl treats as no admin rights.
     */
    public void runMenu(Scanner in, PrintStream out, Session session) {
        this.session = session;
        if (contentMenu != null) {
            effects.banner(out);
            contentMenu.run(in, out, session);
            return;
        }
        runMenu(in, out);
    }

    /** True when the admin-only options should be drawn for this user. */
    boolean showsAdminOptions() {
        return access.canViewAuditLog(session);
    }

    public void runMenu(Scanner in, PrintStream out) {
        effects.banner(out);
        while (true) {
            out.println("\n1) List missions\n2) Read a mission\n3) Search\n4) Exit");
            out.print("Choose: " );
            if (!in.hasNextLine()) {
                return;
            }
            switch (in.nextLine().trim()) {
                case "1": showList(out); break;
                case "2": readMission(in, out); break;
                case "3": search(in, out); break;
                case "4": out.println("Goodbye."); return;
                default: out.println("Please enter 1-4.");
            }
        }
    }

    private void showList(PrintStream out) {
        effects.loading(out, "Decrypting records");
        try {
            List<Mission> missions = control.listMissions();
            if (missions == null || missions.isEmpty()) {
                out.println("No missions available.");
                return;
            }
            printTitles(missions, out);
        } catch (ProgramControlException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void printTitles(List<Mission> missions, PrintStream out) {
        for (int i = 0; i < missions.size(); i++) {
            out.println((i+1) + ". " + missions.get(i).getTitle());
        }
    }

    private void readMission(Scanner in, PrintStream out) {
        out.print("Mission number: ");
        if (!in.hasNextLine()) {
            return;
        }
        String input = in.nextLine().trim();
        int number;
        try {
            number = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            out.println("'" + input + "' is not a mission number.");
            return;
        }
        effects.loading(out, "Decrypting mission " + number);
        try {
            List<Mission> missions = control.listMissions();
            if (missions == null || number < 1 || number > missions.size()) {
                out.println("There is no mission numbered " + number + ".");
                return;
            }
            Mission mission = missions.get(number-1);
            out.println(mission.getTitle() + " (" + mission.getDate() + ")");
            out.println(mission.getBrief());
        } catch (ProgramControlException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void search(Scanner in, PrintStream out) {
        out.print("Search for: ");
        if (!in.hasNextLine()) {
            return;
        }
        try {
            List<Mission> matches = control.searchMissions(in.nextLine());
            if (matches == null || matches.isEmpty()) {
                out.println("No matches found.");
                return;
            }
            printTitles(matches, out);
        } catch (ProgramControlException e) {
            out.println("Error: " + e.getMessage());
        }
    }
}
