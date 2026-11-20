package model;

import java.util.List;

/** The administrator's view of the stored web sessions. */
public interface SessionAdministration {

    /** Sessions that have not timed out, oldest first. */
    List<WebSessionInfo> activeSessions();

    /** End the session with that id. False when there is no such session. */
    boolean revokeSession(String id);
}