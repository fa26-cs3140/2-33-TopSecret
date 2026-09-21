import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvalidCipherExceptionTest {

    @Test
    void carriesMessage() {
        assertEquals("bad key", new InvalidCipherException("bad key").getMessage());
    }

    @Test
    void canBeThrownAndCaught() {
        InvalidCipherException e = assertThrows(InvalidCipherException.class, () -> {
            throw new InvalidCipherException("bad key");
        });
        assertEquals("bad key", e.getMessage());
    }
}