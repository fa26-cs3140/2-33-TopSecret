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

    public Optional<ContentEntry> countryFor(int facilityId, Session session) throws ProgramControlException {
        return content.countryFor(facilityId, session);
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
                       Integer countryId, Session session) throws ProgramControlException {
        requireEditor(session);
        validateValues(type, values);
        if (type == ContentType.MISSION) {
            requireId(facilityId);
            requireId(agentId);
            requireNoCountry(countryId);
            content.addMission(values, facilityId, agentId, session);
        } else if (type == ContentType.FACILITY && countryId != null) {
            requireId(countryId);
            requireNoRelationships(facilityId, agentId);
            content.addFacility(values, countryId, session);
        } else {
            requireNoRelationships(facilityId, agentId);
            requireNoCountry(countryId);
            content.add(type, values, session);
        }
    }

    private static void requireNoRelationships(Integer facilityId, Integer agentId)
            throws WebValidationException {
        if (facilityId != null || agentId != null) {
            throw new WebValidationException("Unexpected relationship fields.");
        }
    }

    private static void requireNoCountry(Integer countryId) throws WebValidationException {
        if (countryId != null) {
            throw new WebValidationException("Unexpected relationship fields.");
        }
    }

    @Override
    public void update(ContentType type, int id, List<String> values, Integer countryId,
                       Session session) throws ProgramControlException {
        requireEditor(session);
        requireId(id);
        validateValues(type, values);
        if (type == ContentType.FACILITY && countryId != null) {
            requireId(countryId);
            content.updateFacility(id, values, countryId, session);
            return;
        }
        requireNoCountry(countryId);
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
