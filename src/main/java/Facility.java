import java.util.Objects;

/** A facility to which agents and missions may be assigned. */
public final class Facility {
    private final int id;
    private final String name;
    private final String abbreviation;

    public Facility(int id, String name, String abbreviation) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        this.id = id;
        this.name = requireText(name, "name");
        this.abbreviation = requireText(abbreviation, "abbreviation");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Facility)) {
            return false;
        }
        Facility facility = (Facility) other;
        return id == facility.id && name.equals(facility.name)
                && abbreviation.equals(facility.abbreviation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, abbreviation);
    }

    @Override
    public String toString() {
        return "Facility{" + "id=" + id + ", name='" + name + '\''
                + ", abbreviation='" + abbreviation + '\'' + '}';
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
