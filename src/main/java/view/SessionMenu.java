package view;

import controller.ProgramControlException;
import model.Session;
import model.SessionAdmin;

import java.io.PrintStream;
import java.util.Scanner;

/** The terminal screen where an administrator lists and revokes web sessions. */
public class SessionMenu {

    public SessionMenu(SessionAdmin sessions) {
    }

    public void run(Scanner in, PrintStream out, Session session) throws ProgramControlException {
        throw new UnsupportedOperationException("not implemented yet");
    }
}