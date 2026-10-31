import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The tables and column names each content type maps onto. */
class ContentTypeTest {

    @Test
    void briefsMapToTheirTableAndColumns() {
        assertEquals("briefs", ContentType.BRIEF.table);
        assertEquals(List.of("title", "date", "text"), ContentType.BRIEF.fields);
    }

    @Test
    void agentsMapToTheirTableAndColumns() {
        assertEquals("agents", ContentType.AGENT.table);
        assertEquals(
                List.of("name", "date_of_birth", "date_of_death", "notes", "facility_id"),
                ContentType.AGENT.fields);
    }

    @Test
    void facilitiesMapToTheirTableAndColumns() {
        assertEquals("facilities", ContentType.FACILITY.table);
        assertEquals(List.of("name", "abbreviation"), ContentType.FACILITY.fields);
    }

    @Test
    void everyTypeHasATableAndAtLeastOneColumn() {
        for (ContentType type : ContentType.values()) {
            assertFalse(type.table.isBlank(), type + " needs a table");
            assertFalse(type.fields.isEmpty(), type + " needs columns");
        }
    }

    @Test
    void theColumnListCannotBeChanged() {
        assertThrows(UnsupportedOperationException.class,
                () -> ContentType.BRIEF.fields.add("extra"));
    }

    @Test
    void eachNameRoundTripsThroughValueOf() {
        for (ContentType type : ContentType.values()) {
            assertEquals(type, ContentType.valueOf(type.name()));
        }
    }
}
