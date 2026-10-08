import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TSV parsing and argument rules. Nothing here opens a database. */
class SqliteMissionRepositoryTest {

    private static final Path UNUSED_DATABASE = Paths.get("unused.db");

    private SqliteMissionRepository repository() {
        return new SqliteMissionRepository(
                new TsvFileHandler(null), UNUSED_DATABASE, "unused.tsv");
    }

    /* constructor rules */

    @Test
    void constructorRejectsNullFileHandler() {
        assertThrows(NullPointerException.class,
                () -> new SqliteMissionRepository(null, UNUSED_DATABASE, "x.tsv"));
    }

    @Test
    void constructorRejectsNullDatabasePath() {
        assertThrows(NullPointerException.class,
                () -> new SqliteMissionRepository(new TsvFileHandler(null), null, "x.tsv"));
    }

    @Test
    void constructorRejectsNullTsvPath() {
        assertThrows(NullPointerException.class,
                () -> new SqliteMissionRepository(new TsvFileHandler(null), UNUSED_DATABASE, null));
    }

    @Test
    void defaultDatabasePathEndsWithTheDatabaseFileName() {
        assertTrue(SqliteMissionRepository.defaultDatabasePath()
                .endsWith(Paths.get("data").resolve(SqliteMissionRepository.DATABASE_FILE)));
    }

    /* parsing the standard file */

    @Test
    void parsesTitleDateAndTextColumns() throws MissionRepositoryException {
        String tsv = "Title\tDate\tText\n"
                + "Operation Sandtrap\t1970-11-03\tBug the diplomatic pouch.\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals(1, missions.size());
        assertEquals("Operation Sandtrap", missions.get(0).getTitle());
        assertEquals("Bug the diplomatic pouch.", missions.get(0).getBrief());
        assertEquals(LocalDate.of(1970, 11, 3), missions.get(0).getDate());
    }

    @Test
    void keepsFileOrder() throws MissionRepositoryException {
        String tsv = "Title\tDate\tText\n"
                + "First\t1970-01-01\tOne.\n"
                + "Second\t1971-01-01\tTwo.\n"
                + "Third\t1972-01-01\tThree.\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals(List.of("First", "Second", "Third"),
                missions.stream().map(Mission::getTitle).toList());
    }

    @Test
    void readsColumnsByHeaderNameNotPosition() throws MissionRepositoryException {
        String tsv = "Date\tText\tTitle\n"
                + "1984-03-16\tTrack the kidnappers.\tThe Beirut Wire\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals("The Beirut Wire", missions.get(0).getTitle());
        assertEquals("Track the kidnappers.", missions.get(0).getBrief());
        assertEquals(LocalDate.of(1984, 3, 16), missions.get(0).getDate());
    }

    @Test
    void headerMatchingIgnoresCaseAndSpacing() throws MissionRepositoryException {
        String tsv = " TITLE \t date \t TEXT \n"
                + "Mission\t1999-12-31\tBrief.\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals("Mission", missions.get(0).getTitle());
    }

    @Test
    void acceptsBriefAsAnAlternativeToTextHeader() throws MissionRepositoryException {
        String tsv = "Title\tDate\tBrief\n"
                + "Mission\t1999-12-31\tThe brief body.\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals("The brief body.", missions.get(0).getBrief());
    }

    /* whitespace and line endings */

    @Test
    void skipsBlankLines() throws MissionRepositoryException {
        String tsv = "Title\tDate\tText\n"
                + "\n"
                + "First\t1970-01-01\tOne.\n"
                + "   \n"
                + "Second\t1971-01-01\tTwo.\n"
                + "\n";

        assertEquals(2, repository().parseTsv(tsv).size());
    }

    @Test
    void handlesWindowsLineEndings() throws MissionRepositoryException {
        String tsv = "Title\tDate\tText\r\n"
                + "First\t1970-01-01\tOne.\r\n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals(1, missions.size());
        assertEquals("One.", missions.get(0).getBrief());
    }

    @Test
    void trimsSurroundingWhitespaceFromCells() throws MissionRepositoryException {
        String tsv = "Title\tDate\tText\n"
                + "  Spaced Title  \t  1970-01-01  \t  Spaced brief.  \n";

        List<Mission> missions = repository().parseTsv(tsv);

        assertEquals("Spaced Title", missions.get(0).getTitle());
        assertEquals("Spaced brief.", missions.get(0).getBrief());
    }

    /* empty input */

    @Test
    void nullContentGivesAnEmptyList() throws MissionRepositoryException {
        assertTrue(repository().parseTsv(null).isEmpty());
    }

    @Test
    void blankContentGivesAnEmptyList() throws MissionRepositoryException {
        assertTrue(repository().parseTsv("   \n  \n").isEmpty());
    }

    @Test
    void headerOnlyGivesAnEmptyList() throws MissionRepositoryException {
        assertTrue(repository().parseTsv("Title\tDate\tText\n").isEmpty());
    }

    /* malformed input */

    @Test
    void missingTitleColumnIsRejected() {
        String tsv = "Date\tText\n1970-01-01\tOne.\n";

        MissionRepositoryException thrown = assertThrows(MissionRepositoryException.class,
                () -> repository().parseTsv(tsv));

        assertTrue(thrown.getMessage().contains("Title"));
    }

    @Test
    void missingDateColumnIsRejected() {
        String tsv = "Title\tText\nFirst\tOne.\n";

        assertThrows(MissionRepositoryException.class, () -> repository().parseTsv(tsv));
    }

    @Test
    void missingTextColumnIsRejected() {
        String tsv = "Title\tDate\nFirst\t1970-01-01\n";

        assertThrows(MissionRepositoryException.class, () -> repository().parseTsv(tsv));
    }

    @Test
    void rowWithTooFewColumnsIsRejected() {
        String tsv = "Title\tDate\tText\n"
                + "Lonely Title\n";

        MissionRepositoryException thrown = assertThrows(MissionRepositoryException.class,
                () -> repository().parseTsv(tsv));

        assertTrue(thrown.getMessage().contains("missing columns"));
    }

    @Test
    void unparseableDateIsRejected() {
        String tsv = "Title\tDate\tText\n"
                + "First\tnot-a-date\tOne.\n";

        MissionRepositoryException thrown = assertThrows(MissionRepositoryException.class,
                () -> repository().parseTsv(tsv));

        assertTrue(thrown.getMessage().contains("not a valid date"));
    }

    @Test
    void emptyDateCellIsRejected() {
        String tsv = "Title\tDate\tText\n"
                + "First\t\tOne.\n";

        assertThrows(MissionRepositoryException.class, () -> repository().parseTsv(tsv));
    }

    /* Serves one canned TSV body and nothing else. */
    private static class TsvFileHandler implements FileHandler {

        private final String tsv;

        TsvFileHandler(String tsv) {
            this.tsv = tsv;
        }

        @Override
        public String getFileList() {
            return "";
        }

        @Override
        public String readFile(int fileNumber) {
            return null;
        }

        @Override
        public String readKey(String keyFileName) {
            return null;
        }

        @Override
        public String readText(String relativePath) {
            return tsv;
        }
    }
}
