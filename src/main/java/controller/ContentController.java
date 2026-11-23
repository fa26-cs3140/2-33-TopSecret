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

    public List<ContentEntry> agentsAt(int facilityId, Session session) throws ProgramControlException {
        return content.agentsAt(facilityId, session);
    }

    public List<ContentEntry> missionsAt(int facilityId, Session session) throws ProgramControlException {
        return content.missionsAt(facilityId, session);
    }

    public List<ContentEntry> missionsFor(int agentId, Session session) throws ProgramControlException {
        return content.missionsFor(agentId, session);
    }

    public Optional<ContentEntry> facilityOf(int agentId, Session session) throws ProgramControlException {
        return content.facilityOf(agentId, session);
    }

    public List<ContentEntry> facilitiesIn(int countryId, Session session) throws ProgramControlException {
        return content.facilitiesIn(countryId, session);
    }

    public Optional<ContentEntry> missionOf(int briefId, Session session) throws ProgramControlException {
        return content.missionOf(briefId, session);
    }

    public List<ContentHit> search(String query, Session session) throws ProgramControlException {
        return content.search(query, session);
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

    public void createBrief(List<String> values, int missionId, Session session) throws ProgramControlException {
        requireEditor(session);
        requireId(missionId);
        validateValues(ContentType.BRIEF, values);
        content.addBrief(values, missionId, session);
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

    /** Fields a record may leave empty. Everything else is required. */
    private static final java.util.Set<String> OPTIONAL = java.util.Set.of("date_of_death", "notes");

    private static void validateValues(ContentType type, List<String> values) throws WebValidationException {
        if (values == null || values.size() != type.fields.size()) {
            throw new WebValidationException("Incorrect number of fields.");
        }
        for (int index = 0; index < values.size(); index++) {
            validateField(type.fields.get(index), values.get(index));
        }
        if (type == ContentType.AGENT) {
            validateAgentDates(values);
        }
    }

    /**
     * One field, by the shape of its name. Any field ending in _id is a record
     * reference, so country_id is checked by the same rule as facility_id and
     * the message says which field was rejected rather than naming facilities
     * whatever the type.
     */
    private static void validateField(String field, String value) throws WebValidationException {
        if (value == null || (value.isBlank() && !OPTIONAL.contains(field))) {
            throw new WebValidationException("Enter a value for " + field + ".");
        }
        if (field.contains("date") && !value.isBlank()) {
            parseDate(field, value);
        }
        if (field.endsWith("_id")) {
            validateIdField(field, value);
        }
    }

    private static void validateIdField(String field, String value) throws WebValidationException {
        int id;
        try {
            id = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new WebValidationException("Choose a record for " + field + ".");
        }
        if (id < 1) {
            throw new WebValidationException("Choose a record for " + field + ".");
        }
    }

    private static java.time.LocalDate parseDate(String field, String value)
            throws WebValidationException {
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.DateTimeException exception) {
            throw new WebValidationException("Enter " + field + " as YYYY-MM-DD.");
        }
    }

    private static void validateAgentDates(List<String> values) throws WebValidationException {
        String birth = values.get(1);
        String death = values.get(2);
        if (!death.isBlank() && parseDate("date_of_death", death)
                .isBefore(parseDate("date_of_birth", birth))) {
            throw new WebValidationException("date_of_death cannot come before date_of_birth.");
        }
    }
}
