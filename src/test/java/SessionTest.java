import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @Test
    void constructorRejectsNullUsername() {
        assertThrows(NullPointerException.class, () -> new Session(null, Role.ADMIN));
    }

    @Test
    void constructorRejectsNullRole() {
        assertThrows(NullPointerException.class, () -> new Session("agent", null));
    }

    @Test
    void gettersReturnWhatWentIn() {
        Session session = new Session("agent", Role.STANDARD);

        assertEquals("agent", session.getUsername());
        assertEquals(Role.STANDARD, session.getRole());
    }

    @Test
    void adminSessionIsAdmin() {
        assertTrue(new Session("boss", Role.ADMIN).isAdmin());
    }

    @Test
    void standardSessionIsNotAdmin() {
        assertFalse(new Session("agent", Role.STANDARD).isAdmin());
    }

    @Test
    void sessionsWithTheSameUserAndRoleAreEqual() {
        Session first = new Session("agent", Role.ADMIN);
        Session second = new Session("agent", Role.ADMIN);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void aSessionEqualsItself() {
        Session session = new Session("agent", Role.ADMIN);

        assertEquals(session, session);
    }

    @Test
    void adifferentUsernameIsNotEqual() {
        assertNotEquals(new Session("agent", Role.ADMIN), new Session("other", Role.ADMIN));
    }

    @Test
    void aDifferentRoleIsNotEqual() {
        assertNotEquals(new Session("agent", Role.ADMIN), new Session("agent", Role.STANDARD));
    }

    @Test
    void otherTypesAndNullAreNotEqual() {
        Session session = new Session("agent", Role.ADMIN);

        assertNotEquals(session, "agent");
        assertNotEquals(session, null);
    }

    @Test
    void toStringNamesTheUserAndTheRole() {
        assertEquals("agent (STANDARD)", new Session("agent", Role.STANDARD).toString());
    }
}
