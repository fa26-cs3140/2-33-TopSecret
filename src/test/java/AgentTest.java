import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    @Test
    void constructorAndGettersExposeEveryField() {
        LocalDate birth = LocalDate.of(1985, 6, 14);
        LocalDate death = LocalDate.of(2024, 2, 1);

        Agent agent = new Agent(2, "Vanguard", birth, death, "Field operative", 4);

        assertEquals(2, agent.getId());
        assertEquals("Vanguard", agent.getName());
        assertEquals(birth, agent.getDateOfBirth());
        assertEquals(death, agent.getDateOfDeath());
        assertEquals("Field operative", agent.getNotes());
        assertEquals(4, agent.getFacilityId());
    }

    @Test
    void deathDateIsOptionalAndNotesMayBeEmpty() {
        Agent agent = new Agent(2, "Vanguard", LocalDate.of(1985, 6, 14), null, "", 4);

        assertNull(agent.getDateOfDeath());
        assertEquals("", agent.getNotes());
    }

    @Test
    void valueMethodsUseAllFields() {
        Agent first = new Agent(2, "Vanguard", LocalDate.of(1985, 6, 14), null, "", 4);
        Agent same = new Agent(2, "Vanguard", LocalDate.of(1985, 6, 14), null, "", 4);
        Agent different = new Agent(3, "Vanguard", LocalDate.of(1985, 6, 14), null, "", 4);

        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "not an agent");
        assertTrue(first.toString().contains("Vanguard"));
    }

    @Test
    void constructorRejectsInvalidRequiredFieldsAndChronology() {
        LocalDate birth = LocalDate.of(1985, 6, 14);

        assertThrows(IllegalArgumentException.class,
                () -> new Agent(0, "Agent", birth, null, "", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(1, " ", birth, null, "", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(1, "Agent", birth, null, "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(1, "Agent", birth, birth.minusDays(1), "", 1));
        assertThrows(NullPointerException.class,
                () -> new Agent(1, null, birth, null, "", 1));
        assertThrows(NullPointerException.class,
                () -> new Agent(1, "Agent", null, null, "", 1));
        assertThrows(NullPointerException.class,
                () -> new Agent(1, "Agent", birth, null, null, 1));
    }
}
