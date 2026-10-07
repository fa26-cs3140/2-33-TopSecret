import java.time.LocalDate;
import java.util.Objects;

/** A mission record shared by the database, control, search, and UI layers. */
public final class Mission {

    private final int id;
    private final String title;
    private final String brief;
    private final LocalDate date;

    public Mission(int id, String title, String brief, LocalDate date) {
        this.id = id;
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.brief = Objects.requireNonNull(brief, "brief cannot be null");
        this.date = Objects.requireNonNull(date, "date cannot be null");
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBrief() {
        return brief;
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Mission)) {
            return false;
        }
        Mission mission = (Mission) other;
        return id == mission.id
                && title.equals(mission.title)
                && brief.equals(mission.brief)
                && date.equals(mission.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, brief, date);
    }

    @Override
    public String toString() {
        return "Mission{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", brief='" + brief + '\'' +
                ", date=" + date +
                '}';
    }
}
