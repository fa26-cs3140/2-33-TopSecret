package model;

import controller.ProgramControlException;

import java.util.List;

public interface ContentService {
    List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException;
    ContentEntry read(ContentType type, int id, Session session) throws ProgramControlException;
    void add(ContentType type, List<String> values, Session session) throws ProgramControlException;
    void update(ContentType type, int id, List<String> values, Session session) throws ProgramControlException;
    void delete(ContentType type, int id, Session session) throws ProgramControlException;
    List<AuditLogEntry> audit(Session session) throws ProgramControlException;

    /** The briefs attached to that mission, newest id last. Empty when it has none. */
    List<ContentEntry> briefsFor(int missionId, Session session) throws ProgramControlException;

    /** Point an existing brief at an existing mission, moving it if it already had one. */
    void attachBrief(int missionId, int briefId, Session session) throws ProgramControlException;
}
