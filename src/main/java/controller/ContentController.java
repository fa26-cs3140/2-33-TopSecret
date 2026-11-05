package controller;

import model.*;
import java.util.List;

public class ContentController {
    private final ContentService content;
    private final AuthenticationService authentication;
    private final AccessControl access = new AccessControl();

    public ContentController(ContentService content, AuthenticationService authentication) {
        this.content = content;
        this.authentication = authentication;
    }

    public Session sessionFor(String username) throws CredentialException {
        return authentication.sessionFor(username);
    }

    public boolean canViewAudit(Session session) {
        return access.canViewAuditLog(session);
    }

    public List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException {
        return content.list(type, session);
    }

    public ContentEntry read(ContentType type, int id, Session session) throws ProgramControlException {
        return content.read(type, id, session);
    }

    public List<AuditLogEntry> audit(Session session) throws ProgramControlException {
        if (!canViewAudit(session)) {
            throw new ProgramControlException("Access denied.");
        }
        return content.audit(session);
    }
}
