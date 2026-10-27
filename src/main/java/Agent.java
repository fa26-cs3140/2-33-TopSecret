import java.time.LocalDate;
import java.util.Objects;

/** An agent assigned to exactly one facility. */
public final class Agent {
    private final int id;
    private final String name;
    private final LocalDate dateOfBirth;
    private final LocalDate dateOfDeath;
    private final String notes;
    private final int facilityId;

    public Agent(int id, String name, LocalDate dateOfBirth, LocalDate dateOfDeath,
                 String notes, int facilityId) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (facilityId < 1) {
            throw new IllegalArgumentException("facilityId must be positive");
        }
        this.id = id;
        this.name = requireText(name, "name");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth cannot be null");
        if (dateOfDeath != null && dateOfDeath.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException("dateOfDeath cannot precede dateOfBirth");
        }
        this.dateOfDeath = dateOfDeath;
        this.notes = Objects.requireNonNull(notes, "notes cannot be null");
        this.facilityId = facilityId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public LocalDate getDateOfDeath() {
        return dateOfDeath;
    }

    public String getNotes() {
        return notes;
    }

    public int getFacilityId() {
        return facilityId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Agent)) {
            return false;
        }
        Agent agent = (Agent) other;
        return id == agent.id && facilityId == agent.facilityId
                && name.equals(agent.name) && dateOfBirth.equals(agent.dateOfBirth)
                && Objects.equals(dateOfDeath, agent.dateOfDeath) && notes.equals(agent.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dateOfBirth, dateOfDeath, notes, facilityId);
    }

    @Override
    public String toString() {
        return "Agent{" + "id=" + id + ", name='" + name + '\''
                + ", dateOfBirth=" + dateOfBirth + ", dateOfDeath=" + dateOfDeath
                + ", notes='" + notes + '\'' + ", facilityId=" + facilityId + '}';
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
