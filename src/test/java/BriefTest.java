import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefTest {

    @Test
    void constructorAndGettersExposeEveryField() {
        LocalDate date = LocalDate.of(1970, 11, 3);

        Brief brief = new Brief(7, "Operation Sandtrap", date, "Observe the diplomatic pouch.");

        assertEquals(7, brief.getId());
        assertEquals("Operation Sandtrap", brief.getTitle());
        assertEquals(date, brief.getDate());
        assertEquals("Observe the diplomatic pouch.", brief.getText());
    }

    @Test
    void valueMethodsUseAllFields() {
        Brief first = new Brief(7, "Operation Sandtrap", LocalDate.of(1970, 11, 3), "Text");
        Brief same = new Brief(7, "Operation Sandtrap", LocalDate.of(1970, 11, 3), "Text");
        Brief different = new Brief(8, "Operation Sandtrap", LocalDate.of(1970, 11, 3), "Text");

        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "not a brief");
        assertTrue(first.toString().contains("Operation Sandtrap"));
    }

    @Test
    void constructorRejectsInvalidRequiredFields() {
        LocalDate date = LocalDate.of(1970, 11, 3);

        assertThrows(IllegalArgumentException.class, () -> new Brief(0, "Title", date, "Text"));
        assertThrows(IllegalArgumentException.class, () -> new Brief(1, " ", date, "Text"));
        assertThrows(IllegalArgumentException.class, () -> new Brief(1, "Title", date, " "));
        assertThrows(NullPointerException.class, () -> new Brief(1, null, date, "Text"));
        assertThrows(NullPointerException.class, () -> new Brief(1, "Title", null, "Text"));
        assertThrows(NullPointerException.class, () -> new Brief(1, "Title", date, null));
    }
}
