import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityTest {

    @Test
    void constructorAndGettersExposeEveryField() {
        Facility facility = new Facility(3, "Meridian Observatory", "MO");

        assertEquals(3, facility.getId());
        assertEquals("Meridian Observatory", facility.getName());
        assertEquals("MO", facility.getAbbreviation());
    }

    @Test
    void valueMethodsUseAllFields() {
        Facility first = new Facility(3, "Meridian Observatory", "MO");
        Facility same = new Facility(3, "Meridian Observatory", "MO");
        Facility different = new Facility(4, "Meridian Observatory", "MO");

        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "not a facility");
        assertTrue(first.toString().contains("Meridian Observatory"));
    }

    @Test
    void constructorRejectsInvalidRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new Facility(0, "Facility", "FA"));
        assertThrows(IllegalArgumentException.class, () -> new Facility(1, " ", "FA"));
        assertThrows(IllegalArgumentException.class, () -> new Facility(1, "Facility", " "));
        assertThrows(NullPointerException.class, () -> new Facility(1, null, "FA"));
        assertThrows(NullPointerException.class, () -> new Facility(1, "Facility", null));
    }
}
