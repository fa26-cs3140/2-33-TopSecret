package model;

import controller.ProgramControlException;

import java.util.List;
import java.util.Optional;

public interface ContentService {
    List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException;
    ContentEntry read(ContentType type, int id, Session session) throws ProgramControlException;
    void add(ContentType type, List<String> values, Session session) throws ProgramControlException;
    int addBrief(List<String> values, int missionId, Session session) throws ProgramControlException;
    void update(ContentType type, int id, List<String> values, Session session) throws ProgramControlException;
    void delete(ContentType type, int id, Session session) throws ProgramControlException;
    List<AuditLogEntry> audit(Session session) throws ProgramControlException;

    /** The briefs attached to that mission, newest id last. Empty when it has none. */
    List<ContentEntry> briefsFor(int missionId, Session session) throws ProgramControlException;

    /** Point an existing brief at an existing mission, moving it if it already had one. */
    void attachBrief(int missionId, int briefId, Session session) throws ProgramControlException;

    /** The facility running that mission, empty when it has none or the mission is unknown. */
    Optional<ContentEntry> facilityFor(int missionId, Session session) throws ProgramControlException;

    /** Record which facility runs a mission, replacing any facility it already had. */
    void setFacility(int missionId, int facilityId, Session session) throws ProgramControlException;

    /** The agents assigned to that mission, whatever facility they belong to. */
    List<ContentEntry> agentsFor(int missionId, Session session) throws ProgramControlException;

    /** Put an agent on a mission. Assigning the same agent again changes nothing. */
    void assignAgent(int missionId, int agentId, Session session) throws ProgramControlException;

    /**
     * Create a mission together with the facility that runs it and its first
     * agent, in one transaction, and answer the new mission's id. A mission
     * requires both, so this is the only way to create one that does not
     * briefly break its own rules.
     */
    int addMission(List<String> values, int facilityId, int agentId, Session session)
            throws ProgramControlException;

    /**
     * The country that facility is assigned to, empty when it has none or the
     * facility is unknown. Read through the foreign key, so renaming a country
     * is visible here immediately.
     */
    Optional<ContentEntry> countryFor(int facilityId, Session session) throws ProgramControlException;

    /** Assign a facility to a country, replacing any country it already had. */
    void setCountry(int facilityId, int countryId, Session session) throws ProgramControlException;

    /**
     * Create a facility together with the country it is assigned to, in one
     * transaction, and answer the new facility's identifier. The caller cannot
     * know that identifier before the row exists, so supplying the country
     * separately would leave the facility briefly without one.
     */
    int addFacility(List<String> values, int countryId, Session session)
            throws ProgramControlException;

    /**
     * Change a facility's fields and its country in one statement. All three
     * columns live on the same row, so an atomic update is both simpler and
     * safer than an update followed by a separate assignment.
     */
    void updateFacility(int facilityId, List<String> values, int countryId, Session session)
            throws ProgramControlException;
    /** The agents who belong to that facility. Empty when it has none or is unknown. */
    List<ContentEntry> agentsAt(int facilityId, Session session) throws ProgramControlException;

    /** The missions run by that facility. */
    List<ContentEntry> missionsAt(int facilityId, Session session) throws ProgramControlException;

    /** The missions that agent is assigned to. */
    List<ContentEntry> missionsFor(int agentId, Session session) throws ProgramControlException;

    /** The facility an agent belongs to, empty when the agent is unknown. */
    Optional<ContentEntry> facilityOf(int agentId, Session session) throws ProgramControlException;

    /** The facilities assigned to that country. */
    List<ContentEntry> facilitiesIn(int countryId, Session session) throws ProgramControlException;

    /** The mission a brief is attached to, empty when it is attached to none. */
    Optional<ContentEntry> missionOf(int briefId, Session session) throws ProgramControlException;

    /**
     * Every record, of every type, with a text field containing the phrase.
     * Case-insensitive, and % and _ in the phrase are ordinary characters.
     * Identifier columns are not searched. Each hit is audited as a READ.
     */
    List<ContentHit> search(String query, Session session) throws ProgramControlException;

}


