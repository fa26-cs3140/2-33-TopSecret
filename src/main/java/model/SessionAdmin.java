package model;

import controller.ProgramControlException;

import java.util.List;

/** Lets an administrator list and revoke web sessions, and audits each revocation. */
public class SessionAdmin {

    public SessionAdmin(SessionAdministration store, AuditLogRepository auditLog) {
    }

    public List<WebSessionInfo> list(Session session) throws ProgramControlException {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public boolean revoke(String id, Session session) throws ProgramControlException {
        throw new UnsupportedOperationException("not implemented yet");
    }
}