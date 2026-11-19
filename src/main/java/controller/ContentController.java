package controller;

import model.*;
import java.util.List;
import java.util.Optional;

public class ContentController implements WebEditingWorkflow {
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

    public Optional<ContentEntry> facilityFor(int missionId, Session session) throws ProgramControlException {
        return content.facilityFor(missionId, session);
    }

    public List<ContentEntry> agentsFor(int missionId, Session session) throws ProgramControlException {
        return content.agentsFor(missionId, session);
    }

    public List<ContentEntry> briefsFor(int missionId, Session session) throws ProgramControlException {
        return content.briefsFor(missionId, session);
    }

    public List<AuditLogEntry> audit(Session session) throws ProgramControlException {
        if (!canViewAudit(session)) {
            throw new ProgramControlException("Access denied.");
        }
        return content.audit(session);
    }

    @Override
    public WebEditForm newForm(ContentType type, Session session) throws ProgramControlException {
        requireEditor(session);
        return WebEditForm.newRecord(type);
    }

    @Override
    public WebEditForm editForm(ContentType type, int id, Session session) throws ProgramControlException {
        requireEditor(session);
        requireId(id);
        return WebEditForm.editRecord(type, content.read(type, id, session));
    }

    @Override
    public void create(ContentType type, List<String> values, Integer facilityId, Integer agentId,
                       Session session) throws ProgramControlException {
        requireEditor(session);
        validateValues(type, values);
        if (type == ContentType.MISSION) {
            requireId(facilityId);
            requireId(agentId);
            content.addMission(values, facilityId, agentId, session);
        } else {
            if (facilityId != null || agentId != null) {
                throw new WebValidationException("Unexpected relationship fields.");
            }
            content.add(type, values, session);
        }
    }

    @Override
    public void update(ContentType type, int id, List<String> values, Session session)
            throws ProgramControlException {
        requireEditor(session);
        requireId(id);
        validateValues(type, values);
        content.update(type, id, values, session);
    }

    private void requireEditor(Session session) throws ProgramControlException {
        if (!access.canCreateContent(session)) {
            throw new ProgramControlException("Administrator access required.");
        }
    }

    private static void requireId(Integer id) throws WebValidationException {
        if (id == null || id < 1) {
            throw new WebValidationException("Choose a valid record ID.");
        }
    }

    private static void validateValues(ContentType type, List<String> values) throws WebValidationException {
        if (values == null || values.size() != type.fields.size()) {
            throw new WebValidationException("Incorrect number of fields.");
        }
        try {
            for (int index = 0; index < values.size(); index++) {
                String field = type.fields.get(index);
                String value = values.get(index);
                if (value == null || (value.isBlank() && !field.equals("date_of_death") && !field.equals("notes"))) {
                    throw new IllegalArgumentException();
                }
                if (field.contains("date") && !value.isBlank()) {
                    java.time.LocalDate.parse(value);
                }
                if (field.equals("facility_id")) {
                    requireId(Integer.parseInt(value));
                }
            }
            if (type == ContentType.AGENT && !values.get(2).isBlank()
                    && java.time.LocalDate.parse(values.get(2)).isBefore(java.time.LocalDate.parse(values.get(1)))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new WebValidationException("Enter required fields, valid ISO dates (YYYY-MM-DD), and a positive facility ID.");
        }
    }
}
