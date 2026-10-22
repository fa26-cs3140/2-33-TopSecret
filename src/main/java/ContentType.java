import java.util.List;

public enum ContentType {
    BRIEF("briefs", List.of("title", "date", "text")),
    AGENT("agents", List.of("name", "date_of_birth", "date_of_death", "notes", "facility_id")),
    FACILITY("facilities", List.of("name", "abbreviation"));

    final String table;
    final List<String> fields;

    ContentType(String table, List<String> fields) {
        this.table = table;
        this.fields = fields;
    }
}
