import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The role parts of Credential. The rest is covered by CredentialTest. */
class CredentialRoleTest {

    @Test
    void theTwoArgumentConstructorMakesAStandardUser() {
        assertEquals(Role.STANDARD, new Credential("agent", "Secret1").getRole());
    }

    @Test
    void theThreeArgumentConstructorKeepsTheRoleGiven() {
        assertEquals(Role.ADMIN, new Credential("boss", "Secret1", Role.ADMIN).getRole());
    }

    @Test
    void theConstructorRejectsANullRole() {
        assertThrows(NullPointerException.class, () -> new Credential("agent", "Secret1", null));
    }

    @Test
    void theConstructorStillRejectsANullUsernameOrPassword() {
        assertThrows(NullPointerException.class, () -> new Credential(null, "Secret1", Role.ADMIN));
        assertThrows(NullPointerException.class, () -> new Credential("agent", null, Role.ADMIN));
    }

    @Test
    void anAdminCredentialIsAdmin() {
        assertTrue(new Credential("boss", "Secret1", Role.ADMIN).isAdmin());
    }

    @Test
    void aStandardCredentialIsNotAdmin() {
        assertFalse(new Credential("agent", "Secret1", Role.STANDARD).isAdmin());
    }

    @Test
    void withPasswordChangesThePasswordAndKeepsEverythingElse() {
        Credential original = new Credential("boss", "Secret1", Role.ADMIN);

        Credential changed = original.withPassword("Newpass1");

        assertEquals("boss", changed.getUsername());
        assertEquals("Newpass1", changed.getPassword());
        assertEquals(Role.ADMIN, changed.getRole());
    }

    @Test
    void withRoleChangesTheRoleAndKeepsEverythingElse() {
        Credential original = new Credential("agent", "Secret1", Role.STANDARD);

        Credential changed = original.withRole(Role.ADMIN);

        assertEquals("agent", changed.getUsername());
        assertEquals("Secret1", changed.getPassword());
        assertEquals(Role.ADMIN, changed.getRole());
    }

    @Test
    void withPasswordLeavesTheOriginalAlone() {
        Credential original = new Credential("boss", "Secret1", Role.ADMIN);

        original.withPassword("Newpass1");

        assertEquals("Secret1", original.getPassword());
    }

    @Test
    void withRoleLeavesTheOriginalAlone() {
        Credential original = new Credential("agent", "Secret1", Role.STANDARD);

        original.withRole(Role.ADMIN);

        assertEquals(Role.STANDARD, original.getRole());
    }

    @Test
    void withPasswordCanClearThePassword() {
        Credential cleared = new Credential("agent", "Secret1", Role.STANDARD).withPassword("");

        assertFalse(cleared.hasPassword());
        assertEquals(Role.STANDARD, cleared.getRole());
    }

    @Test
    void withRoleRejectsNull() {
        Credential original = new Credential("agent", "Secret1", Role.STANDARD);

        assertThrows(NullPointerException.class, () -> original.withRole(null));
    }

    @Test
    void withPasswordRejectsNull() {
        Credential original = new Credential("agent", "Secret1", Role.STANDARD);

        assertThrows(NullPointerException.class, () -> original.withPassword(null));
    }
}
