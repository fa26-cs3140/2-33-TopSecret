import java.util.List;

public interface ContentService {
    List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException;
    ContentEntry read(ContentType type, int id, Session session) throws ProgramControlException;
    void add(ContentType type, List<String> values, Session session) throws ProgramControlException;
    void update(ContentType type, int id, List<String> values, Session session) throws ProgramControlException;
    void delete(ContentType type, int id, Session session) throws ProgramControlException;
    List<AuditLogEntry> audit(Session session) throws ProgramControlException;
}
