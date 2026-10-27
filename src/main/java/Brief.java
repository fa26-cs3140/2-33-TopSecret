import java.time.LocalDate;
import java.util.Objects;

/** One independently stored brief. */
public final class Brief {
    private final int id;
    private final String title;
    private final LocalDate date;
    private final String text;

    public Brief(int id, String title, LocalDate date, String text) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        this.id = id;
        this.title = requireText(title, "title");
        this.date = Objects.requireNonNull(date, "date cannot be null");
        this.text = requireText(text, "text");
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Brief)) {
            return false;
        }
        Brief brief = (Brief) other;
        return id == brief.id && title.equals(brief.title)
                && date.equals(brief.date) && text.equals(brief.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, date, text);
    }

    @Override
    public String toString() {
        return "Brief{" + "id=" + id + ", title='" + title + '\''
                + ", date=" + date + ", text='" + text + '\'' + '}';
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
