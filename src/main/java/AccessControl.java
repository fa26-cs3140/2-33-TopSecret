/**
 * Every access rule in one place. The menu asks it what to draw and the layer
 * doing the work asks it again, so a hidden option is still refused if someone
 * types its number.
 */
public class AccessControl {

    // a missing session means no access, never full access
    public boolean canReadContent(Session session) {
        return session != null;
    }

    public boolean canCreateContent(Session session) {
        return isAdmin(session);
    }

    public boolean canCreateUsers(Session session) {
        return isAdmin(session);
    }

    public boolean canViewAuditLog(Session session) {
        return isAdmin(session);
    }

    public boolean canManageRoles(Session session) {
        return isAdmin(session);
    }

    // any logged-in user may leave a message for an agent
    public boolean canSendMessage(Session session) {
        return session != null;
    }

    // reading is limited to your own mail by the inbox query, not by role
    public boolean canReadMessages(Session session) {
        return session != null;
    }

    private static boolean isAdmin(Session session) {
        return session != null && session.isAdmin();
    }
}
