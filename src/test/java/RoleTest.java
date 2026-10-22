import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleTest {

    @Test
    void thereAreTwoRoles() {
        assertEquals(2, Role.values().length);
    }

    @Test
    void valueOfReadsBothNames() {
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"));
        assertEquals(Role.STANDARD, Role.valueOf("STANDARD"));
    }

    @Test
    void valueOfRejectsAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("WIZARD"));
    }

    @Test
    void fromFileValueReadsBothNames() {
        assertEquals(Role.ADMIN, Role.fromFileValue("ADMIN"));
        assertEquals(Role.STANDARD, Role.fromFileValue("STANDARD"));
    }

    @Test
    void fromFileValueIgnoresCase() {
        assertEquals(Role.ADMIN, Role.fromFileValue("admin"));
        assertEquals(Role.STANDARD, Role.fromFileValue("Standard"));
    }

    @Test
    void fromFileValueGivesNullForJunk() {
        assertNull(Role.fromFileValue("wizard"));
        assertNull(Role.fromFileValue(""));
        assertNull(Role.fromFileValue(null));
    }

    @Test
    void toFileValueIsTheName() {
        assertEquals("ADMIN", Role.ADMIN.toFileValue());
        assertEquals("STANDARD", Role.STANDARD.toFileValue());
    }

    @Test
    void everyRoleSurvivesAFileRoundTrip() {
        for (Role role : Role.values()) {
            assertEquals(role, Role.fromFileValue(role.toFileValue()));
        }
    }
}
