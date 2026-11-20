package model;

import controller.ProgramControlException;

import java.util.List;
import java.util.Objects;

/**
 * Lets an administrator list and revoke web sessions. Revoking is the only
 * thing recorded in the audit log: listing the sessions reveals no record
 * content, and a timeout is the system acting, not a person.
 */
public class SessionAdmin {
    private final SessionAdministration store;
    private final AuditLogRepository auditLog;
    private final AccessControl access = new AccessControl();

    public SessionAdmin(SessionAdministration store, AuditLogRepository auditLog) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog cannot be null");
    }

    public List<WebSessionInfo> list(Session session) throws ProgramControlException {
        require(session);
        return store.activeSessions();
    }

    /** True when a session was ended. Each revocation is audited as a DELETE of a SESSION. */
    public boolean revoke(String id, Session session) throws ProgramControlException {
        require(session);
        if (!store.revokeSession(id)) {
            return false;
        }
        try {
            // a web session has no numeric id, so the audit row carries 0
            auditLog.record(AuditEventType.DELETE, AuditContentType.SESSION, 0, session.getUsername());
        } catch (AuditLogException exception) {
            throw new ProgramControlException("The session was revoked but could not be audited.", exception);
        }
        return true;
    }

    private void require(Session session) throws ProgramControlException {
        if (!access.canManageSessions(session)) {
            throw new ProgramControlException("Access denied.");
        }
    }
}