import java.util.List;

public record ContentEntry(int id, List<String> values) {
    public ContentEntry {
        values = List.copyOf(values);
    }
}
