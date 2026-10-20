import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
public class CredentialTest {

    @Test
    void constructorStoresUsernameAndPassword() {
        Credential credential = new Credential("agent-1", "Secret1");

    assertEquals("agent-1", credential.getUsername());
    assertEquals("Secret1", credential.getPassword());
    }


    @Test
    void hasPasswordIsTrueWhenPasswordIsSet() {
        assertTrue(new Credential("agent-1", "Secret1").hasPassword());
    }

    @Test
    void hasPAsswordIsFalseWhenPasswordIsEmpty() {
        assertFalse(new Credential("agent-1", "").hasPassword());
    }

    @Test
    void nullUsernameIsRejected() {
        assertThrows(NullPointerException.class, () -> new Credential(null, "Secret1"));
    }

    @Test
    void nullPassWordIsRejected() {
        assertThrows(NullPointerException.class, () -> new Credential("agent-1", null));
    }
}
