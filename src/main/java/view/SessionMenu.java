package view;

import controller.ProgramControlException;
import model.Session;
import model.SessionAdmin;
import model.WebSessionInfo;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/** The terminal screen where an administrator lists and revokes web sessions. */
public class SessionMenu {
    private final SessionAdmin sessions;

    public SessionMenu(SessionAdmin sessions) {
        this.sessions = sessions;
    }

    public void run(Scanner in, PrintStream out, Session session) throws ProgramControlException {
        List<WebSessionInfo> active = sessions.list(session);
        if (active.isEmpty()) {
            out.println("No active web sessions.");
            return;
        }
        out.println("Active web sessions (each ends after 30 minutes without a request):");
        for (WebSessionInfo info : active) {
            out.println(info.id() + "  " + info.username()
                    + "  signed in " + info.createdAt() + "  last active " + info.lastSeen());
        }
        out.print("Session ID to revoke (blank to go back): ");
        if (!in.hasNextLine()) {
            return;
        }
        String id = in.nextLine().trim();
        if (id.isEmpty()) {
            return;
        }
        out.println(sessions.revoke(id, session) ? "Session revoked." : "No session with that ID.");
    }
}