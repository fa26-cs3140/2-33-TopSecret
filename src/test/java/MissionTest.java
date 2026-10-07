import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissionTest {

    @Test
    void constructorStoresMissionFields() {
        LocalDate date = LocalDate.of(1983, 11, 7);

        Mission mission = new Mission(12, "Operation Able Archer", "Monitor readiness.", date);

        assertEquals(12, mission.getId());
        assertEquals("Operation Able Archer", mission.getTitle());
        assertEquals("Monitor readiness.", mission.getBrief());
        assertEquals(date, mission.getDate());
    }

    @Test
    void constructorRejectsNullFields() {
        LocalDate date = LocalDate.of(1983, 11, 7);

        assertThrows(NullPointerException.class,
                () -> new Mission(1, null, "Brief", date));
        assertThrows(NullPointerException.class,
                () -> new Mission(1, "Title", null, date));
        assertThrows(NullPointerException.class,
                () -> new Mission(1, "Title", "Brief", null));
    }
}
